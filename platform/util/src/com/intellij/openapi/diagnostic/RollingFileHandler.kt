// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.StreamHandler

class RollingFileHandler @JvmOverloads constructor(
  val logPath: Path,
  val limit: Long,
  val count: Int,
  val append: Boolean,
  private val onRotate: Runnable? = null
) : StreamHandler() {
  @Volatile private lateinit var meter: MeteredOutputStream

  private class MeteredOutputStream(private val delegate: OutputStream, @Volatile var written: Long) : OutputStream() {
    override fun write(b: Int) {
      delegate.write(b)
      written++
    }

    override fun write(b: ByteArray) {
      delegate.write(b)
      written += b.size
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      delegate.write(b, off, len)
      written += len
    }

    override fun close() = delegate.close()

    override fun flush() = delegate.flush()
  }

  init {
    encoding = StandardCharsets.UTF_8.name()
    open(append)
  }

  private fun open(append: Boolean) {
    Files.createDirectories(logPath.parent)
    val delegate = BufferedOutputStream(Files.newOutputStream(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))
    meter = MeteredOutputStream(delegate, if (append) Files.size(logPath) else 0)
    setOutputStream(meter)
  }

  override fun publish(record: LogRecord) {
    if (!isLoggable(record)) return
    super.publish(record)
    flush()
    if (limit > 0 && meter.written >= limit) {
      synchronized(this) {
        if (meter.written >= limit) {
          rotate()
        }
      }
    }
  }

  private fun rotate() {
    onRotate?.run()

    try {
      Files.deleteIfExists(logPathWithIndex(count))
      for (i in count-1 downTo 1) {
        val path = logPathWithIndex(i)
        if (Files.exists(path)) {
          Files.move(path, logPathWithIndex(i+1), StandardCopyOption.ATOMIC_MOVE)
        }
      }
    }
    catch (e: IOException) {
      // rotate failed, keep writing to existing log
      super.publish(LogRecord(Level.SEVERE, "Log rotate failed: ${e.message}").also { it.thrown = e })
      return
    }

    close()

    val e = try {
      Files.move(logPath, logPathWithIndex(1), StandardCopyOption.ATOMIC_MOVE)
      null
    }
    catch (e: IOException) {
      e
    }

    open(false)

    if (e != null) {
      super.publish(LogRecord(Level.SEVERE, "Log rotate failed: ${e.message}").also { it.thrown = e })
    }
  }

  private fun logPathWithIndex(index: Int): Path {
    val pathString = logPath.toString()
    val extIndex = pathString.lastIndexOf('.')
    return Paths.get(pathString.substring(0, extIndex) + ".$index" + pathString.substring(extIndex))
  }
}
