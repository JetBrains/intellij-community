// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.TestOnly
import java.io.*
import java.nio.file.Path
import java.util.function.Supplier

/**
 * File writer with rotation when the file reaches a [maxFileSizeInBytes] and deletion by [maxFileAge].
 *
 * @param dir Directory where all log files will be created.
 * @param maxFileSizeInBytes New file will be created when maxFileSize exceed.
 * @param maxFileAge How long file should be stored since last modification.
 * @param logFilePathProvider Function to build new log file path.
 */
open class EventLogFileWriter(
  private val dir: Path,
  private val maxFileSizeInBytes: Int,
  private val logFilePathProvider: (dir: Path) -> File,
  private val maxFileAge: Long = 7 * 24 * 60 * 60 * 100
) : AutoCloseable {
  private val lock = Any() // protects all mutable fields
  private val currentFileData: FileData by lazy { FileData(dir, logFilePathProvider) }
  private var closed = false
  protected var oldestExistingFile = -1L
  private var logFilesSupplier: Supplier<List<File>> = Supplier {
    val files = dir.toFile().listFiles()
    if (files == null || files.isEmpty()) emptyList() else files.toList()
  }
  private var lastCriticalFailureTimestamp = -1L

  @TestOnly
  constructor(dir: Path,
              maxFileSize: Int,
              maxFileAge: Long,
              logFilePathProvider: (dir: Path) -> File,
              logFilesSupplier: Supplier<List<File>>) : this(dir, maxFileSize, logFilePathProvider, maxFileAge) {
    this.logFilesSupplier = logFilesSupplier
  }

  open fun getActiveLogName(): String {
    synchronized(lock) {
      if (!currentFileData.getLogFile().exists()) {
        rollOver()
      }
      return currentFileData.getLogFile().name
    }
  }

  fun log(text: String) {
    synchronized(lock) {
      try {
        if (closed) throw IllegalStateException("Attempt to use closed FUS log")
        val outputStream = currentFileData.getCountingOutputStream()
        outputStream.write(text.toByteArray())
        outputStream.write('\n'.code)
        if (outputStream.bytesWritten > maxFileSizeInBytes) {
          rollOver()
          cleanUpOldFiles()
        }
      }
      catch (e: IOException) {
        val currentTimeMillis = System.currentTimeMillis()
        //To prevent infinite spamming to stderr
        if (lastCriticalFailureTimestamp != -1L && currentTimeMillis - lastCriticalFailureTimestamp < 10000L) {
          System.err.println("Failed to write to FUS log")
          lastCriticalFailureTimestamp = currentTimeMillis
        }
      }
    }
  }

  /**
   * Force logger to create new file
   */
  @Throws(IOException::class)
  fun rollOver() {
    synchronized(lock) {
      currentFileData.close()
      currentFileData.initialize()
    }
  }

  override fun close() {
    synchronized(lock) {
      try {
        currentFileData.close()
        closed = true
      }
      catch (e: IOException) {
        System.err.println("Failed to close FUS log")
      }
    }
  }

  protected open fun cleanUpOldFiles() {
    val oldestAcceptable = System.currentTimeMillis() - maxFileAge
    if (oldestExistingFile != -1L && oldestAcceptable < oldestExistingFile) {
      return
    }
    cleanUpOldFiles(oldestAcceptable)
  }

  protected open fun cleanUpOldFiles(oldestAcceptable: Long) {
    synchronized(lock) {
      val logs = logFilesSupplier.get()
      if (logs.isEmpty()) {
        return
      }
      val activeLog = getActiveLogName()
      var oldestFile: Long = -1
      for (file in logs) {
        if (StringUtil.equals(file.name, activeLog)) continue
        val lastModified = file.lastModified()
        if (lastModified < oldestAcceptable) {
          if (!file.delete()) {
            System.err.println("Failed deleting old FUS file $file")
          }
        }
        else if (lastModified < oldestFile || oldestFile == -1L) {
          oldestFile = lastModified
        }
      }
      oldestExistingFile = oldestFile
    }
  }

  fun cleanUp() {
    synchronized(lock) {
      currentFileData.close()
      val files = logFilesSupplier.get()
      for (file in files) {
        if (!file.delete()) {
          System.err.println("Failed deleting old FUS file $file")
        }
      }
      rollOver()
    }
  }

  @Throws(IOException::class)
  fun flush() {
    synchronized(lock) {
      currentFileData.getCountingOutputStream().flush()
    }
  }
}

private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
  var bytesWritten: Long = 0L

  override fun write(b: Int) {
    delegate.write(b)
    bytesWritten++
  }

  override fun write(b: ByteArray) {
    delegate.write(b)
    bytesWritten += b.size
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    delegate.write(b, off, len)
    bytesWritten += len
  }

  override fun close() {
    delegate.close()
  }

  override fun flush() {
    delegate.flush()
  }
}

private class FileData(private val dir: Path, private val logFilePathProvider: (dir: Path) -> File) {
  private var logFile: File? = null
  private var countingOutputStream: CountingOutputStream? = null

  private fun isInitialized(): Boolean = logFile != null && countingOutputStream != null

  fun initialize() {
    logFile = logFilePathProvider(dir)
    logFile!!.parentFile.mkdirs()
    logFile!!.createNewFile()
    countingOutputStream = CountingOutputStream(BufferedOutputStream(FileOutputStream(logFile!!)))
  }

  fun close() {
    if (isInitialized()) {
      countingOutputStream!!.flush()
      countingOutputStream!!.close()
      countingOutputStream = null
      logFile = null
    }
  }

  fun getLogFile(): File {
    if (!isInitialized()) initialize()
    return logFile!!
  }

  fun getCountingOutputStream(): CountingOutputStream {
    if (!isInitialized()) initialize()
    return countingOutputStream!!
  }
}