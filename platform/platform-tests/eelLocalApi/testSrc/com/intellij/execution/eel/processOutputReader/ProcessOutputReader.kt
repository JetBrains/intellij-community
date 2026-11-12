// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel.processOutputReader

import com.intellij.execution.eel.processOutputReader.impl.TTYEmulator
import com.intellij.execution.eel.processOutputReader.impl.putLine
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import io.ktor.util.decodeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Provides access to process output.
 * Create with [ProcessOutputReader], and [close] after use!
 *
 * To get output, use [get]
 */
internal interface ProcessOutputReader : AutoCloseable {
  fun get(stream: OutStream): String
}

internal enum class OutStream {
  STDOUT, STDERR
}

/**
 * For [TTY]-based output, you have width and height, but no stderr (it is redirected to stdout, so [ProcessOutputReader.get] doesn't make difference)
 */
internal sealed interface OutputType {
  data class NoTTY(val stderr: EelReceiveChannel) : OutputType
  data class TTY(val width: Int, val height: Int) : OutputType
}


/**
 * Create [ProcessOutputReader] instance. Call [ProcessOutputReader.close] when finished!
 * It reads data from streams (in background coroutine) and eventually makes it available via [ProcessOutputReader.get]
 */
internal fun CoroutineScope.ProcessOutputReader(stdout: EelReceiveChannel, outputType: OutputType): ProcessOutputReader {
  when (outputType) {
    is OutputType.NoTTY -> {
      val stdout = ProcessOutConsumerImpl.NoTTY().start(this, stdout)
      val stderr = ProcessOutConsumerImpl.NoTTY().start(this, outputType.stderr)
      return object : ProcessOutputReader {
        override fun get(stream: OutStream): String =
          when (stream) {
            OutStream.STDOUT -> stdout
            OutStream.STDERR -> stderr
          }.output

        override fun close() = Unit
      }
    }
    is OutputType.TTY -> {
      val tty = TTYEmulator(this, outputType.width, outputType.height)
      val out = ProcessOutConsumerImpl.TTY(tty).start(this, stdout)
      return object : ProcessOutputReader, AutoCloseable by tty {
        override fun get(stream: OutStream): String = out.output
      }
    }
  }
}

// impl

private abstract class ProcessOutConsumerImpl {
  abstract fun appendLine(line: String)
  protected abstract val dirtyOutput: String
  val output: String get() = dirtyOutput.replace(JUNK, "")

  class NoTTY : ProcessOutConsumerImpl() {
    private val _lines = ConcurrentLinkedDeque<String>()
    override val dirtyOutput: String get() = _lines.joinToString("")

    override fun appendLine(line: String) {
      _lines.add(line)
    }

  }

  class TTY(private val tty: TTYEmulator) : ProcessOutConsumerImpl() {
    override fun appendLine(line: String) {
      tty.putLine(line)
    }

    override val dirtyOutput: String get() = tty.getLines()

  }
}

private fun ProcessOutConsumerImpl.start(scope: CoroutineScope, src: EelReceiveChannel): ProcessOutConsumerImpl {
  scope.launch {
    copyFrom(src)
  }
  return this
}

private suspend fun ProcessOutConsumerImpl.copyFrom(src: EelReceiveChannel) {
  val buffer = ByteBuffer.allocate(8192)
  while (src.receive(buffer) != ReadResult.EOF) {
    val line = buffer.flip().decodeString()
    appendLine(line)
    buffer.clear()
  }
}

private val JUNK = Regex("[\\s \n\r]+")