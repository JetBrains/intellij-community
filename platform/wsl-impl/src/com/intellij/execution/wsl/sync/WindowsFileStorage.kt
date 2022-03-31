// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.wsl.AbstractWslDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.*
import net.jpountz.xxhash.XXHashFactory
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CompletableFuture
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.notExists

private val LOGGER = Logger.getInstance(WindowsFileStorage::class.java)

private class MyFileVisitor(private val onlyExtensions: Array<String>,
                            private val rootDir: Path,
                            private val processFile: (relativeToDir: FilePathRelativeToDir, file: Path, attrs: BasicFileAttributes) -> Unit) : SimpleFileVisitor<Path>() {
  private val dirLinksInt: MutableMap<FilePathRelativeToDir, FilePathRelativeToDir> = mutableMapOf()
  val dirLinks: Map<FilePathRelativeToDir, FilePathRelativeToDir> get() = dirLinksInt
  override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
    return super.postVisitDirectory(dir, exc)
  }

  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    if (!(attrs.isRegularFile)) return FileVisitResult.CONTINUE
    if (onlyExtensions.isNotEmpty() && file.extension !in onlyExtensions) { // Skip because we don't care about this extension
      return FileVisitResult.CONTINUE
    }
    val name = rootDir.relativize(file).joinToString("/").lowercase()
    processFile(FilePathRelativeToDir(name), file, attrs)
    return FileVisitResult.CONTINUE
  }

  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = if (FileSystemUtil.getAttributes(
      dir.toFile())?.isSymLink == true) {
    val target = FileSystemUtil.resolveSymLink(dir.toFile())?.let { rootDir.resolve(it) }
    if (target != null && target.isDirectory() && target.startsWith(rootDir)) {
      dirLinksInt[FilePathRelativeToDir(rootDir.relativize(dir).toString())] = FilePathRelativeToDir(rootDir.relativize(target).toString())
    }
    FileVisitResult.SKIP_SUBTREE
  }
  else {
    FileVisitResult.CONTINUE
  }
}

class WindowsFileStorage(dir: Path,
                         distro: AbstractWslDistribution,
                         onlyExtensions: Array<String>) : FileStorage<WindowsFilePath, LinuxFilePath>(dir, distro, onlyExtensions) {
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

  override fun getHashesAndLinks(skipHashCalculation: Boolean): Pair<List<WslHashRecord>, Map<FilePathRelativeToDir, FilePathRelativeToDir>> {
    val result = ArrayList<WslHashRecord>(AVG_NUM_FILES)
    val hashTool = XXHashFactory.nativeInstance().hash64() // Native hash can access direct (mapped) buffer a little-bit faster
    val visitor = MyFileVisitor(onlyExtensions, dir) { relativeToDir: FilePathRelativeToDir, file: Path, attrs: BasicFileAttributes ->
      if (skipHashCalculation || attrs.size() == 0L) { // Empty file's hash is 0, see wslhash.c
        result.add(WslHashRecord(relativeToDir, 0))
      }
      else { // Map file and read hash
        FileChannel.open(file, StandardOpenOption.READ).use {
          val buf = it.map(FileChannel.MapMode.READ_ONLY, 0, attrs.size())
          try {
            result.add(WslHashRecord(relativeToDir, hashTool.hash(buf, 0))) // Seed 0 is default, see wslhash.c
          }
          finally {
            ByteBufferUtil.cleanBuffer(buf) // Unmap file: can't overwrite mapped file
          }
        }
      }
    }
    val time = TimeoutUtil.measureExecutionTime<Throwable> {
      Files.walkFileTree(dir, visitor)
    }
    LOGGER.info("Windows files calculated in $time")
    return Pair(result, visitor.dirLinks)
  }

  override fun isEmpty(): Boolean = dir.notExists() || dir.listDirectoryEntries().isEmpty()
  override fun removeFiles(filesToRemove: Collection<FilePathRelativeToDir>) {
    if (filesToRemove.isEmpty()) return
    for (file in filesToRemove) {
      val fileToDelete = dir.resolve(file.asWindowsPath)
      assert(dir.isAncestor(fileToDelete))
      Files.delete(fileToDelete)
    }
    LOGGER.info("${filesToRemove.size} files removed")
  }

  override fun createTempFile(): Path = createTmpWinFile(distro).first
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
      Compressor.Tar(tarFile.toFile(), Compressor.Tar.Compression.NONE).use { tar ->
        for (relativeFile in files) {
          tar.addFile(relativeFile.asWindowsPath, dir.resolve(relativeFile.asWindowsPath))
        }
      }
    }
    val dest = distro.getUNCRootVirtualFile(true)!!.toNioPath().resolve(destTar)
    feature.get()
    LOGGER.info("Copying")
    Files.copy(tarFile, dest)
    tarFile.delete()
  }

  override fun removeTempFile(file: WindowsFilePath) {
    file.delete()
  }
}
