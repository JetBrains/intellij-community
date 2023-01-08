// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.wsl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.delete
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

private val LOGGER = Logger.getInstance(LinuxFileStorage::class.java)


class LinuxFileStorage(dir: LinuxFilePath, distro: AbstractWslDistribution, onlyExtensions: Array<String>)
  : FileStorage<LinuxFilePath, WindowsFilePath>(dir.trimEnd('/') + '/', distro, onlyExtensions) {

  // Linux side only works with UTF of 7-bit ASCII which is also supported by UTF and WSL doesn't support other charsets

  private val CHARSET = Charsets.UTF_8
  private val FILE_SEPARATOR = CHARSET.encode(":").get()
  private val LINK_SEPARATOR = CHARSET.encode(";").get()

  override fun createSymLinks(links: Map<FilePathRelativeToDir, FilePathRelativeToDir>) {
    val script = createTmpWinFile(distro)
    script.first.writeText(links
                             .map { it.key.escapedWithDir to it.value.escapedWithDir }
                             .joinToString("\n")
                             // No need to create link if parent dir doesn't exist
                             { "[ -e $(dirname ${it.first}) ] && ln -s ${it.second} ${it.first}" })
    runBlocking { distro.createProcess("sh", script.second).getBareExecutionResult() }
    script.first.delete()
  }

  override fun getHashesAndLinks(skipHashCalculation: Boolean): Pair<List<WslHashRecord>, Map<FilePathRelativeToDir, FilePathRelativeToDir>> {
    val hashes = ArrayList<WslHashRecord>(AVG_NUM_FILES)
    val links = HashMap<FilePathRelativeToDir, FilePathRelativeToDir>(AVG_NUM_FILES)
    val time = TimeoutUtil.measureExecutionTime<Throwable> {
      val tool = distro.getTool("wslhash", dir, if (skipHashCalculation) "no_hash" else "hash", *onlyExtensions)
      val process = tool.createProcess()
      process.inputStream.use {
        val hashesAndLinks = getHashesInternal(it)
        hashes += hashesAndLinks.first
        links += hashesAndLinks.second
      }
      runBlocking { process.getResultStdoutStr() }
    }
    LOGGER.info("Linux files calculated in $time")
    return Pair(hashes, links)
  }


  override fun createTempFile(): String = distro.runCommand("mktemp", "-u").getOrThrow()

  override fun removeLinks(vararg linksToRemove: FilePathRelativeToDir) {
    this.removeFiles(linksToRemove.asList())
  }

  override fun isEmpty(): Boolean {
    val options = WSLCommandLineOptions().apply {
      addInitCommand("[ -e ${escapePath(dir)} ]")
    }
    val process = distro.patchCommandLine(GeneralCommandLine("ls", "-A", dir), null, options).createProcess()
    if (!process.waitFor(5, TimeUnit.SECONDS)) throw Exception("Process didn't finish: WSL frozen?")
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
    runCommands(*filesToRemove.map { arrayOf("rm", it.escapedWithDir) }.toTypedArray())
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
  private fun getHashesInternal(toolStdout: InputStream): Pair<List<WslHashRecord>, Map<FilePathRelativeToDir, FilePathRelativeToDir>> {
    val hashes = ArrayList<WslHashRecord>(AVG_NUM_FILES)
    val links = HashMap<FilePathRelativeToDir, FilePathRelativeToDir>(AVG_NUM_FILES)
    val fileOutput = ByteBuffer.wrap(toolStdout.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN)

    // See wslhash.c: format is the following: [file_path]:[hash].
    // Hash is little-endian 8 byte (64 bit) integer
    // or [file_path];[link_len][link] where link_len is 4 byte signed int

    var fileStarted = 0
    val outputLimit = fileOutput.limit()
    while (fileOutput.position() < outputLimit) {
      when (fileOutput.get()) {
        FILE_SEPARATOR -> {
          val hash = fileOutput.long
          val prevPos = fileOutput.position()
          // 9 = 8 bytes long + separator
          val name = CHARSET.decode(fileOutput.limit(prevPos - 9).position(fileStarted)).toString()
          fileOutput.limit(outputLimit).position(prevPos)
          hashes += WslHashRecord(FilePathRelativeToDir(name), hash)
          fileStarted = prevPos
        }
        LINK_SEPARATOR -> {
          val length = fileOutput.int
          val prevPos = fileOutput.position()
          //  5 = 4 bytes int + separator
          val file = CHARSET.decode(fileOutput.limit(prevPos - 5).position(fileStarted)).toString()
          val link = CHARSET.decode(fileOutput.limit(prevPos + length).position(prevPos)).toString()
          fileOutput.limit(outputLimit).position(prevPos + length)
          if (link.startsWith(dir)) {
            links[FilePathRelativeToDir((file))] = FilePathRelativeToDir(link.substring(dir.length))
          }
          fileStarted = prevPos + length
        }
      }
    }
    return Pair(hashes, links)
  }

  private val FilePathRelativeToDir.escapedWithDir: String get() = escapePath(dir + asUnixPath)

  private fun escapePath(path: LinuxFilePath) = GeneralCommandLine(path).commandLineString

  // it is cheaper to run 1-2 commands directly, but long list of command should run as script
  private fun runCommands(vararg commands: Array<String>) {
    if (commands.count() < 3) {
      for (command in commands) {
        distro.runCommand(*command)
      }
    }
    else {
      val script = createTmpWinFile(distro)
      script.first.writeText(commands.joinToString("\n") { it.joinToString(" ") })
      distro.runCommand("sh", script.second)
      script.first.delete()
    }
  }
}

