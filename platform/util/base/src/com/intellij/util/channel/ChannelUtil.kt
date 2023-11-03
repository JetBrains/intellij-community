// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingDeque
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/*
  Solution from fleet.api.exec.ExecApiProcess.kt. Maybe it should be merged somehow
 */
@ApiStatus.Experimental
class ChannelInputStream(channel: ReceiveChannel<ByteArray>) : InputStream() {
  private val myScope = CoroutineScope(EmptyCoroutineContext)

  private sealed class Content {
    class Data(val stream: ByteArrayInputStream) : Content()
    class Error(val cause: Throwable) : Content()
    object End : Content()
  }

  private val myBuffer = LinkedBlockingDeque<Content>()

  init {
    myScope.launch {
      try {
        channel.consumeEach { bytes ->
          myBuffer.offerLast(Content.Data(ByteArrayInputStream(bytes)))
        }
        myBuffer.offerLast(Content.End)
      }
      catch (e: Throwable) {
        if (e is CancellationException) {
          myBuffer.offerLast(Content.End)
        }
        else {
          myBuffer.offerLast(Content.Error(e))
        }
      }
    }
  }

  override fun close() {
    myScope.cancel(CancellationException("ChannelInputStream was closed"))
  }

  override fun read(): Int {
    val available = getAvailableBuffer() ?: return -1
    return available.read()
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val available = getAvailableBuffer() ?: return -1
    return available.read(b, off, minOf(len, available.available()))
  }

  override fun available(): Int {
    return (myBuffer.peekFirst() as? Content.Data)?.stream?.available() ?: 0
  }

  private fun getAvailableBuffer(): ByteArrayInputStream? {
    while (true) {
      val current = kotlin.runCatching { myBuffer.takeFirst() }.getOrNull() ?: return null

      when (current) {
        is Content.End -> {
          myBuffer.putFirst(current)
          return null
        }
        is Content.Error -> {
          myBuffer.putFirst(current)
          throw IOException(current.cause)
        }
        is Content.Data -> {
          if (current.stream.available() > 0) {
            myBuffer.putFirst(current)
            return current.stream
          }
        }
      }
    }
  }
}

private const val MAX_ARRAY_SIZE_SENT: Int = 1024

@ApiStatus.Experimental
class ChannelOutputStream(private val channel: SendChannel<ByteArray>) : OutputStream() {
  override fun write(b: Int) {
    val result = channel.trySendBlocking(byteArrayOf(b.toByte()))
    when {
      result.isClosed -> throw IOException("Unable to write, channel is closed")
      result.isFailure -> throw IOException("Unable to write to channel", result.exceptionOrNull())
    }
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    var offset = off

    while (offset < len) {
      val nextOffset = minOf(offset + MAX_ARRAY_SIZE_SENT, len)
      val result = channel.trySendBlocking(b.copyOfRange(offset, nextOffset))

      when {
        result.isClosed -> throw IOException("Unable to write, channel is closed")
        result.isFailure -> throw IOException("Unable to write to channel", result.exceptionOrNull())
      }
      offset = nextOffset
    }
  }

  override fun close() {
    channel.close()
    super.close()
  }
}