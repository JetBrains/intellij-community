// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.wsl.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.util.TimeoutUtil
import com.intellij.util.io.delete
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.writeText

private val LOGGER = Logger.getInstance(LinuxFileStorage::class.java)


class LinuxFileStorage(dir: LinuxFilePath, distro: AbstractWslDistribution)
  : FileStorage<LinuxFilePath, WindowsFilePath>(dir.trimEnd('/') + '/', distro) {

  // Linux side only works with UTF of 7-bit ASCII which is also supported by UTF and WSL doesn't support other charsets
  private val CHARSET = Charsets.UTF_8

  private val FILE_SEPARATOR: Byte = 0
  private val LINK_SEPARATOR: Byte = 1
  private val STUB_SEPARATOR: Byte = 2

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

  override fun calculateSyncData(filters: WslHashFilters, skipHash: Boolean, useStubs: Boolean): WslSyncData {
    val dataRef = Ref<WslSyncData>()
    val time = TimeoutUtil.measureExecutionTime<Throwable> {
      val wslHashArgs = listOfNotNull(if (skipHash) "-n" else null,
                                      if (useStubs) "-s" else null,
                                      *filters.toArgs().toTypedArray(),
                                      dir)
      val tool = distro.getTool("wslhash", *wslHashArgs.toTypedArray())
      val process = tool.createProcess()
      process.inputStream.use {
        dataRef.set(calculateSyncDataInternal(it))
      }
      runBlocking { process.getResultStdoutStr() }
    }
    LOGGER.info("Linux files calculated in $time")
    return dataRef.get()
  }

  override fun createTempFile(): String = distro.runCommand("mktemp", "-u").getOrThrow()

  override fun createStubs(files: Collection<FilePathRelativeToDir>) {
    val script = createTmpWinFile(distro)
    try {
      val scriptContent = files.joinToString("\n") { "mkdir -p \"$(dirname ${it.escapedWithDir})\" && touch ${it.escapedWithDir}" }
      script.first.writeText(scriptContent)
      runBlocking { distro.createProcess("sh", script.second).getBareExecutionResult() }
    }
    finally {
      script.first.delete()
    }
  }

  override fun removeLinks(vararg linksToRemove: FilePathRelativeToDir) {
    this.removeFiles(linksToRemove.asList())
  }

  override fun isEmpty(): Boolean {
    // echo nonce >&2 && [ -e dir ] && ls dir
    // if exit code is 0 and stdout is empty, the folder is empty
    // if exit code != 0, folder doesn't exist or some error happened
    // we cut everything before nonce because shell profile might print junk there, and then check the rest
    val prefixCutter = PrefixCutter()
    val options = WSLCommandLineOptions().apply {
      addInitCommand("[ -e ${escapePath(dir)} ]")
      addInitCommand("echo ${prefixCutter.token} >&2")
    }
    val process = CapturingProcessHandler(distro.patchCommandLine(GeneralCommandLine("ls", "-A", dir), null, options)).runProcess(5000, true)
    if (process.isTimeout) throw Exception("Process didn't finish: WSL frozen?")
    if (process.exitCode == 0) {
      // Folder exists, let's check if empty
      return process.stdout.isEmpty() //process.inputStream.read() == -1
    }
    else {
      // Folder doesn't exist
      val error = prefixCutter.getAfterToken(process.stderr).trim()
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
    distro.runCommand("cp", linuxTarFile, distro.getWslPathSafe(destTar))
    distro.runCommand("rm", linuxTarFile)
  }

  override fun unTar(tarFile: LinuxFilePath) {
    LOGGER.info("Unpacking")
    distro.runCommand("mkdir", "-p", dir)
    distro.runCommand("tar", "xf", tarFile, "-C", dir)
  }

  /**
   * Parse output from `wslhash` and return [WslSyncData].
   */
  private fun calculateSyncDataInternal(toolStdout: InputStream): WslSyncData {
    val hashes = ArrayList<WslHashRecord>(AVG_NUM_FILES)
    val links = mutableMapOf<FilePathRelativeToDir, FilePathRelativeToDir>()
    val stubs = mutableSetOf<FilePathRelativeToDir>()
    val fileOutput = ByteBuffer.wrap(toolStdout.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN)

    // See wslhash.c.
    // Output format is the following:
    //   [file_path]\0[hash], where hash is little-endian 8 byte (64 bit) integer
    //   [link_path]\1[link_len][link], where link_len is 4 byte signed int
    //   [stub_path]\2
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
        STUB_SEPARATOR -> {
          val prevPos = fileOutput.position()
          // 1 = separator
          val name = CHARSET.decode(fileOutput.limit(prevPos - 1).position(fileStarted)).toString()
          fileOutput.limit(outputLimit).position(prevPos)
          stubs += FilePathRelativeToDir(name)
          fileStarted = prevPos
        }
      }
    }
    return WslSyncData(hashes, links, stubs)
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

  fun markExec(fileToMarkExec: String) {
    distro.runCommand("chmod", "+x", "$dir/$fileToMarkExec").getOrThrow()
  }
}

