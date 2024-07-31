// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines.channel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import kotlin.coroutines.cancellation.CancellationException

/*
  Solution from fleet.api.exec.ExecApiProcess.kt. Maybe it should be merged somehow
 */
@ApiStatus.Experimental
class ChannelInputStream private constructor(
  private val channel: ReceiveChannel<*>,
): InputStream() {
  companion object {
    fun forArrays(parentCoroutineScope: CoroutineScope, channel: ReceiveChannel<ByteArray>): ChannelInputStream {
      val result = ChannelInputStream(channel)
      parentCoroutineScope.launch {
        consumeChannel(channel, result.myBuffer) { ByteBuffer.wrap(it) }
      }
      return result
    }

    fun forByteBuffers(parentCoroutineScope: CoroutineScope, channel: ReceiveChannel<ByteBuffer>): ChannelInputStream {
      val result = ChannelInputStream(channel)
      parentCoroutineScope.launch {
        consumeChannel(channel, result.myBuffer) { it }
      }
      return result
    }

    private suspend inline fun <T> consumeChannel(channel: ReceiveChannel<T>, myBuffer: LinkedBlockingDeque<Content>, crossinline transform: (T) -> ByteBuffer) {
      try {
        channel.consumeEach { obj ->
          val bytes = transform(obj)
          if (bytes.hasRemaining()) {
            myBuffer.offerLast(Content.Data(bytes))
          }
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

  private val myBuffer = LinkedBlockingDeque<Content>()

  private sealed class Content {
    class Data(val buffer: ByteBuffer) : Content()
    class Error(val cause: Throwable) : Content()
    object End : Content()
  }

  override fun close() {
    channel.cancel(CancellationException("ChannelInputStream was closed"))
  }

  override fun read(): Int {
    val available = getAvailableBuffer() ?: return -1
    return available.get().toInt()
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val available = getAvailableBuffer() ?: return -1
    val resultSize = minOf(len, available.remaining())
    available.get(b, off, resultSize)
    return resultSize
  }

  override tailrec fun available(): Int =
    when (val current = myBuffer.pollFirst()) {
      null -> 0

      is Content.Data -> {
        val availableInCurrent = current.buffer.remaining()
        if (availableInCurrent > 0) {
          myBuffer.putFirst(current)
          availableInCurrent
        }
        else {
          available()
        }
      }

      Content.End, is Content.Error -> {
        myBuffer.putFirst(current)
        0
      }
    }

  private fun getAvailableBuffer(): ByteBuffer? {
    while (true) {
      val current =
        try {
          myBuffer.takeFirst()
        }
        catch (ignored: InterruptedException) {
          return null
        }

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
          if (current.buffer.hasRemaining()) {
            myBuffer.putFirst(current)
            return current.buffer
          }
        }
      }
    }
  }
}

private const val MAX_ARRAY_SIZE_SENT: Int = 1024

@ApiStatus.Experimental
sealed class ChannelOutputStream<T>(private val channel: SendChannel<T>) : OutputStream() {
  companion object {
    fun forArrays(channel: SendChannel<ByteArray>): ChannelOutputStream<ByteArray> =
      ForByteArray(channel)

    fun forByteBuffers(channel: SendChannel<ByteBuffer>): ChannelOutputStream<ByteBuffer> =
      ForByteBuffer(channel)
  }

  private class ForByteArray(channel: SendChannel<ByteArray>) : ChannelOutputStream<ByteArray>(channel) {
    override fun fromByte(b: Byte): ByteArray = byteArrayOf(b)
    override fun range(b: ByteArray, offset: Int, nextOffset: Int): ByteArray = b.copyOfRange(offset, nextOffset)
  }

  private class ForByteBuffer(channel: SendChannel<ByteBuffer>) : ChannelOutputStream<ByteBuffer>(channel) {
    override fun fromByte(b: Byte): ByteBuffer = ByteBuffer.wrap(byteArrayOf(b))
    override fun range(b: ByteArray, offset: Int, nextOffset: Int): ByteBuffer = ByteBuffer.wrap(b, offset, nextOffset)
  }

  @ApiStatus.Internal
  protected abstract fun fromByte(b: Byte): T

  @ApiStatus.Internal
  protected abstract fun range(b: ByteArray, offset: Int, nextOffset: Int): T

  override fun write(b: Int) {
    val result = channel.trySendBlocking(fromByte(b.toByte()))
    when {
      result.isClosed -> throw IOException("Unable to write, channel is closed")
      result.isFailure -> throw IOException("Unable to write to channel", result.exceptionOrNull())
    }
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    var offset = off

    while (offset < len) {
      val nextOffset = minOf(offset + MAX_ARRAY_SIZE_SENT, len)
      val result = channel.trySendBlocking(range(b, offset, nextOffset))

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