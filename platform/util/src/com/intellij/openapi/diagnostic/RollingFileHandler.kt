// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  val onRotate: Runnable? = null
) : StreamHandler() {
  private lateinit var meter: MeteredOutputStream

  private class MeteredOutputStream(
    private val delegate: OutputStream,
    var written: Long,
  ) : OutputStream() {
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

    override fun close() {
      delegate.close()
    }

    override fun flush() {
      delegate.flush()
    }
  }

  init {
    encoding = StandardCharsets.UTF_8.name()
    open(append)
  }

  private fun open(append: Boolean) {
    val fout = Files.newOutputStream(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    val bout = BufferedOutputStream(fout)
    meter = MeteredOutputStream(bout, if (append) Files.size(logPath) else 0)
    setOutputStream(meter)
  }

  override fun publish(record: LogRecord) {
    if (!isLoggable(record)) return
    super.publish(record)
    flush()
    if (limit > 0 && meter.written >= limit) {
      rotate()
    }
  }

  private fun rotate() {
    onRotate?.run()
    try {
      Files.deleteIfExists(logPathWithIndex(count))
      for (i in 1 until count) {
        val path = logPathWithIndex(i)
        if (Files.exists(path)) {
          Files.move(path, logPathWithIndex(i+1), StandardCopyOption.ATOMIC_MOVE)
        }
      }
    }
    catch (e: IOException) {
      // rotate failed, keep writing to existing log
      super.publish(LogRecord(Level.SEVERE, "Log rotate failed: ${e.message}"))
      return
    }
    close()
    try {
      Files.move(logPath, logPathWithIndex(1))
    }
    catch (e: IOException) {
      // ignore?
    }
    open(false)
  }

  private fun logPathWithIndex(index: Int): Path {
    val pathString = logPath.toString()
    val extIndex = pathString.lastIndexOf('.')
    return Paths.get(pathString.substring(0, extIndex) + ".$index" + pathString.substring(extIndex))
  }
}
