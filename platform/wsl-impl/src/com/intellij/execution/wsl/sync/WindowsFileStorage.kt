// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.wsl.AbstractWslDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.*
import com.intellij.util.system.CpuArch
import net.jpountz.xxhash.XXHash64
import net.jpountz.xxhash.XXHashFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.notExists

private val LOGGER = Logger.getInstance(WindowsFileStorage::class.java)

private class MyFileVisitor(private val filters: WslHashFilters,
                            private val rootDir: Path,
                            private val hashTool: XXHash64,
                            private val skipHash: Boolean,
                            private val useStubs: Boolean) : SimpleFileVisitor<Path>() {

  private val _hashes: MutableList<WslHashRecord> = ArrayList(AVG_NUM_FILES)
  private val _dirLinks: MutableMap<FilePathRelativeToDir, FilePathRelativeToDir> = mutableMapOf()
  private val _stubs: MutableSet<FilePathRelativeToDir> = mutableSetOf()

  val hashes: List<WslHashRecord> get() = _hashes
  val dirLinks: Map<FilePathRelativeToDir, FilePathRelativeToDir> get() = _dirLinks
  val stubs: Set<FilePathRelativeToDir> get() = _stubs

  override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
    return super.postVisitDirectory(dir, exc)
  }

  override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
    if (!(attrs.isRegularFile)) return FileVisitResult.CONTINUE
    processFile(FilePathRelativeToDir(rootDir.relativize(path).joinToString("/").lowercase()), path, attrs)
    return FileVisitResult.CONTINUE
  }

  fun processFile(relativeToDir: FilePathRelativeToDir, file: Path, attrs: BasicFileAttributes) {
    if (!filters.isFileNameOk(file.fileName.toString())) {
      if (useStubs) {
        _stubs.add(relativeToDir)
      }
    }
    else if (skipHash || attrs.size() == 0L) { // Empty file's hash is 0, see wslhash.c
      _hashes.add(WslHashRecord(relativeToDir, 0))
    }
    else { // Map file and read hash
      FileChannel.open(file, StandardOpenOption.READ).use {
        val buf = it.map(FileChannel.MapMode.READ_ONLY, 0, attrs.size())
        try {
          _hashes.add(WslHashRecord(relativeToDir, hashTool.hash(buf, 0))) // Seed 0 is default, see wslhash.c
        }
        finally {
          ByteBufferUtil.cleanBuffer(buf) // Unmap file: can't overwrite mapped file
        }
      }
    }
  }

  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = if (FileSystemUtil.getAttributes(
      dir.toFile())?.isSymLink == true) {
    val target = FileSystemUtil.resolveSymLink(dir.toFile())?.let { rootDir.resolve(it) }
    if (target != null && target.isDirectory() && target.startsWith(rootDir)) {
      _dirLinks[FilePathRelativeToDir(rootDir.relativize(dir).toString())] = FilePathRelativeToDir(rootDir.relativize(target).toString())
    }
    FileVisitResult.SKIP_SUBTREE
  }
  else {
    FileVisitResult.CONTINUE
  }
}

class WindowsFileStorage(dir: Path,
                         distro: AbstractWslDistribution) : FileStorage<WindowsFilePath, LinuxFilePath>(dir, distro) {
  private fun runCommand(vararg commands: String) {
    val cmd = arrayOf("cmd", "/c", *commands)
    ExecUtil.execAndGetOutput(GeneralCommandLine(*cmd)).let {
      if (it.exitCode != 0) {
        throw Exception("Can't run command ${cmd.joinToString(" ")}: ${it.stderr}")
      }
    }
  }

  override fun createSymLinks(links: Map<FilePathRelativeToDir, FilePathRelativeToDir>) {
    for ((source, target) in links) {
      val sourceDir = dir.resolve(source.asWindowsPath)
      val targetDir = dir.resolve(target.asWindowsPath)
      if (!sourceDir.parent.exists()) {
        continue // Can't create link in unexisting folder
      }
      runCommand("mklink", "/J", sourceDir.toString(), targetDir.toString())
    }
  }

  override fun calculateSyncData(filters: WslHashFilters, skipHash: Boolean, useStubs: Boolean): WslSyncData {
    val arch = System.getProperty("os.arch")
    val useNativeHash = CpuArch.CURRENT == CpuArch.X86_64
    LOGGER.info("Arch $arch, using native hash: $useNativeHash")
    val hashTool = if (useNativeHash) XXHashFactory.nativeInstance().hash64() else XXHashFactory.safeInstance().hash64() // Native hash can access direct (mapped) buffer a little-bit faster
    val visitor = MyFileVisitor(filters, dir, hashTool, skipHash, useStubs)
    val time = TimeoutUtil.measureExecutionTime<Throwable> {
      Files.walkFileTree(dir, visitor)
    }
    LOGGER.info("Windows files calculated in $time")
    return WslSyncData(visitor.hashes, visitor.dirLinks, visitor.stubs)
  }

  override fun isEmpty(): Boolean = dir.notExists() || dir.listDirectoryEntries().isEmpty()

  override fun removeFiles(filesToRemove: Collection<FilePathRelativeToDir>) {
    if (filesToRemove.isEmpty()) return
    for (file in filesToRemove) {
      val fileToDelete = dir.resolve(file.asWindowsPath)
      assert(fileToDelete.startsWith(dir))
      Files.delete(fileToDelete)
    }
    LOGGER.info("${filesToRemove.size} files removed")
  }

  override fun createTempFile(): Path = createTmpWinFile(distro).first

  override fun createStubs(files: Collection<FilePathRelativeToDir>) {
    for (file in files) {
      val filePath = dir.resolve(file.asWindowsPath)
      if (!filePath.exists()) {
        filePath.createParentDirectories().createFile()
      }
    }
  }

  override fun removeLinks(vararg linksToRemove: FilePathRelativeToDir) {
    for (link in linksToRemove) {
      runCommand("rmdir", dir.resolve(link.asWindowsPath).toString())
    }
  }

  override fun unTar(tarFile: WindowsFilePath) {
    LOGGER.info("Unpacking")
    Decompressor.Tar(tarFile).extract(dir)
  }

  override fun tarAndCopyTo(files: Collection<FilePathRelativeToDir>, destTar: LinuxFilePath) {
    LOGGER.info("Creating tar")
    val tarFile = createTempFile()
    val feature = CompletableFuture.supplyAsync {
      Compressor.Tar(tarFile, Compressor.Tar.Compression.NONE).use { tar ->
        for (relativeFile in files) {
          tar.addFile(relativeFile.asWindowsPath, dir.resolve(relativeFile.asWindowsPath))
        }
      }
    }
    val dest = distro.getUNCRootPath().resolve(destTar)
    feature.get()
    LOGGER.info("Copying")
    Files.copy(tarFile, dest)
    tarFile.delete()
  }

  override fun removeTempFile(file: WindowsFilePath) {
    file.delete()
  }
}
