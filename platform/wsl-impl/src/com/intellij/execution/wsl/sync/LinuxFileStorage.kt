// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.delete
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

private val LOGGER = Logger.getInstance(LinuxFileStorage::class.java)

internal class LinuxFileStorage(dir: LinuxFilePath, distro: AbstractWslDistribution, onlyExtensions: Array<String>)
  : FileStorage<LinuxFilePath, WindowsFilePath>(dir.trimEnd('/') + '/', distro, onlyExtensions) {


  override fun getHashes(): List<WslHashRecord> {
    val result = ArrayList<WslHashRecord>(AVG_NUM_FILES)
    val time = TimeoutUtil.measureExecutionTime<Throwable> {
      val tool = distro.getTool("wslhash", dir, *onlyExtensions)
      val process = tool.createProcess()
      process.inputStream.use {
        result += getHashesInternal(it)
      }
      waitProcess(process, tool.commandLineString)
    }
    LOGGER.info("Linux files calculated in $time")
    return result
  }


  override fun getAllFilesInDir(): Collection<FilePathRelativeToDir> {
    val extCommands = ArrayList<String>(onlyExtensions.size)
    for (ext in onlyExtensions) {
      extCommands += listOf("-name", "*.$ext", "-or")
    }
    extCommands.removeLastOrNull()
    // See find(1)
    return distro.runCommand("find", dir, "-xdev", "-type", "f", *(extCommands.toTypedArray()))
      .splitToSequence('\n')
      .filterNot { it.isBlank() }
      .map { it.substring(dir.length) }
      .toList()
  }

  override fun createTempFile(): String = distro.runCommand("mktemp", "-u")

  override fun isEmpty(): Boolean {
    val options = WSLCommandLineOptions().apply {
      val escapedDir = GeneralCommandLine(dir).commandLineString
      addInitCommand("[ -e $escapedDir ]")
    }
    val process = distro.patchCommandLine(GeneralCommandLine("ls", "-A", dir), null, options).createProcess()
    if (!process.waitFor(5, TimeUnit.SECONDS)) throw Exception("Process didn't finished: WSL frozen?")
    if (process.exitValue() == 0) {
      // Folder exists, lets check if empty
      return process.inputStream.read() == -1
    }
    else {
      // Folder doesn't exist
      val error = process.errorStream.readAllBytes().decodeToString()
      if (error.isEmpty()) return true // Doesn't exist, but still empty
      throw Exception("Error checking folder: $error")
    }
  }

  override fun removeFiles(filesToRemove: Collection<FilePathRelativeToDir>) {
    LOGGER.info("Removing files")
    if (filesToRemove.size < 3) {
      for (file in filesToRemove) {
        distro.runCommand("rm", "$dir/$file")
      }
      return
    }
    val script = createTmpWinFile(distro)
    script.first.writeText(filesToRemove.joinToString("\n") { GeneralCommandLine("rm", "$dir/$it").commandLineString })
    distro.runCommand("sh", script.second)
    script.first.delete()
  }

  override fun removeTempFile(file: LinuxFilePath) {
    distro.runCommand("rm", file)
  }

  override fun tarAndCopyTo(files: Collection<FilePathRelativeToDir>, destTar: WindowsFilePath) {
    val linuxTarFile = createTempFile()
    val listFile = createTmpWinFile(distro)
    listFile.first.writeText(files.joinToString("\n"))

    LOGGER.info("Creating tar")
    // See tar(1)
    distro.runCommand("tar", "cf", linuxTarFile, "-m", "-h", "-O", "-C", dir, "-T", listFile.second)
    listFile.first.delete()

    LOGGER.info("Copying tar")
    distro.runCommand("cp", linuxTarFile, distro.getWslPath(destTar))
    distro.runCommand("rm", linuxTarFile)
  }

  override fun unTar(tarFile: LinuxFilePath) {
    LOGGER.info("Unpacking")
    distro.runCommand("mkdir", "-p", dir)
    distro.runCommand("tar", "xf", tarFile, "-C", dir)
  }

  /**
   * Read `wslhash` stdout and return map of [file->hash]
   */
  private fun getHashesInternal(toolStdout: InputStream): List<WslHashRecord> {
    val result = ArrayList<WslHashRecord>(AVG_NUM_FILES)
    val fileOutput = ByteBuffer.wrap(toolStdout.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN)
    // Linux side only works with UTF of 7-bit ASCII which is also supported by UTF and WSL doesn't support other charsets
    val charset = Charsets.UTF_8
    // See wslhash.c: format is the following: [file_path]:[hash].
    // Hash is little-endian 8 byte (64 bit) integer
    val separator = charset.encode(":").get()
    var fileStarted = 0
    val limit = fileOutput.limit()
    while (fileOutput.position() < limit) {
      val byte = fileOutput.get()
      if (byte == separator) {
        val hash = fileOutput.long
        val prevPos = fileOutput.position()
        // 9 = 8 bytes long + separator
        val message = charset.decode(fileOutput.limit(prevPos - 9).position(fileStarted))
        fileOutput.limit(limit).position(prevPos)
        val name = message.toString()
        result += WslHashRecord(name, hash)
        fileStarted = prevPos
      }
    }
    return result
  }
}

