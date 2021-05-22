// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit


/**
 * An input stream which may have other resources associated that need to be closed / cleaned up when closing the stream.
 * For example, a temporary file may need to be deleted as soon as we are done reading from its input stream.
 */
interface InputHandle : Closeable {
  val inputStream: InputStream
  override fun close() = inputStream.close()
}


class SocketInputHandle(port: Int = 0) : InputHandle {
  val localPort: Int get() = serverSocket.localPort

  private val cleanup = MultiCloseable()
  private val serverSocket = ServerSocket(port).also(cleanup::registerCloseable)

  override val inputStream: InputStream by lazy { serverSocket.accept().getInputStream().also(cleanup::registerCloseable) }
  override fun close() = cleanup.close()  // don't call super.close() to avoid computing inputStream Lazy.
}

class StreamInputHandle(override val inputStream: InputStream) : InputHandle

abstract class FileInputHandle(val path: Path) : InputHandle

@Suppress("SpellCheckingInspection")
class UnixFifoInputHandle(path: Path) : FileInputHandle(path) {
  private val cleanup = MultiCloseable().apply {
    registerCloseable { super.close() }
  }

  init {
    cleanup.runClosingOnFailure {
      mkfifo(path).also {
        cleanup.registerCloseable { Files.delete(path) }
      }

      // We need to open the write end of the pipe to ensure opening the pipe for reading would not block indefinitely
      // in case the real daemon process doesn't open in for writing (for example, if it fails to start).
      Files.newByteChannel(path, StandardOpenOption.READ, StandardOpenOption.WRITE).also(cleanup::registerCloseable)
    }
  }

  override val inputStream: InputStream = Files.newInputStream(path, StandardOpenOption.READ)

  override fun close() = cleanup.close()

  companion object {
    private const val MKFIFO_PROCESS_TIMEOUT_MS = 3000L

    private fun mkfifo(path: Path) {
      val process = ProcessBuilder("mkfifo", "-m", "0666", path.toString()).start()
      try {
        val completed = try {
          Thread.interrupted() // clear
          process.waitFor(MKFIFO_PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        catch (e: InterruptedException) {
          throw IOException("mkfifo interrupted", e)
        }
        if (!completed) {
          throw IOException("mkfifo timed out ($MKFIFO_PROCESS_TIMEOUT_MS ms)")
        }
        if (process.exitValue() != 0) {
          val stderr = String(process.errorStream.readAllBytes(), StandardCharsets.UTF_8)
          throw IOException("mkfifo failed with exit code ${process.exitValue()}: $stderr")
        }
      }
      catch (e: Throwable) {
        process.destroyForcibly().onExit().whenComplete { _, _ ->
          Files.deleteIfExists(path)
        }
        throw e
      }
    }
  }
}
