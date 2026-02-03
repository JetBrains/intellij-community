// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EelSendApi::class)

package com.intellij.execution.eel

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendApi
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.provider.utils.CopyError
import com.intellij.platform.eel.provider.utils.EelChannelClosedException
import com.intellij.platform.eel.provider.utils.EelOutputChannel
import com.intellij.platform.eel.provider.utils.EelPipe
import com.intellij.platform.eel.provider.utils.OnError
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.asOutputStream
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsInputStream
import com.intellij.platform.eel.provider.utils.consumeReceiveChannelAsKotlin
import com.intellij.platform.eel.provider.utils.copy
import com.intellij.platform.eel.provider.utils.ensureClosed
import com.intellij.platform.eel.provider.utils.lines
import com.intellij.platform.eel.provider.utils.readAllBytes
import com.intellij.platform.eel.provider.utils.readWholeText
import com.intellij.platform.eel.provider.utils.sendUntilEnd
import com.intellij.platform.eel.provider.utils.sendWholeBuffer
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import io.ktor.util.decodeString
import io.ktor.util.moveToByteArray
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.easymock.EasyMock.anyInt
import org.easymock.EasyMock.anyObject
import org.easymock.EasyMock.expect
import org.easymock.EasyMock.mock
import org.easymock.EasyMock.replay
import org.easymock.EasyMock.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junitpioneer.jupiter.cartesian.CartesianTest
import org.junitpioneer.jupiter.params.IntRangeSource
import org.opentest4j.AssertionFailedError
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteBuffer.allocate
import java.nio.ByteBuffer.wrap
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

@Suppress("checkedExceptions")
class EelChannelToolsTest {
  companion object {
    private const val TEXT = "Some text"
  }

  val data = TEXT.encodeToByteArray()

  @CartesianTest
  fun testReceiveTextTest(
    @CartesianTest.Values(ints = [1, 3, 5, 4096]) bufferSize: Int,
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ): Unit = timeoutRunBlocking {
    val channel = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel()
    val text = channel.readWholeText(bufferSize)
    assertEquals(TEXT, text)
  }


  @CartesianTest
  fun testInputStreamFull(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ) {
    val src = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel().consumeAsInputStream()
    assertEquals(TEXT, src.readAllBytes().decodeToString())
  }

  @Test
  fun testOutputStreamFull() {
    val dst = ByteArrayOutputStream()
    val dstStream = dst.asEelChannel().asOutputStream()
    dstStream.write(data)
    dstStream.flush()
    assertEquals(TEXT, dst.toByteArray().decodeToString())
  }

  @Test
  @Timeout(10, unit = TimeUnit.SECONDS)
  fun testOutputStreamManualCopy() {
    val dst = ByteArrayOutputStream()
    val dstStream = dst.asEelChannel().asOutputStream()
    for (b in data) {
      dstStream.write(b.toInt())
    }
    dstStream.close()
    assertEquals(TEXT, dst.toByteArray().decodeToString())
  }


  @CartesianTest
  @Timeout(10, unit = TimeUnit.SECONDS)
  fun testInputStreamManualCopy(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ) {
    val src = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel().consumeAsInputStream()
    val list = allocate(8192)
    while (true) {
      val b = src.read()
      if (b == -1) break
      list.put(b.toByte())
    }
    assertEquals(TEXT, list.flip().decodeString())
  }

  @CartesianTest
  fun testInputStreamDifferentSize(
    @CartesianTest.Values(ints = [1, 3, 5, 4096]) bufferSize: Int,
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ) {
    val src = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel().consumeAsInputStream()
    val buffer = ByteArray(bufferSize)
    val wordLimit = min(min(bufferSize, data.size), srcBlockSize)
    assertEquals(wordLimit, src.read(buffer))
    val result = buffer.copyOf(wordLimit)
    assertEquals(TEXT.subSequence(0, wordLimit), result.decodeToString())
  }


  @CartesianTest
  fun testCopyWithError(
    @CartesianTest.Values(booleans = [true, false]) srcErr: Boolean,
    @CartesianTest.Values(booleans = [true, false]) dstErr: Boolean,
  ): Unit = timeoutRunBlocking {

    val srcErrorText = "src err"
    val dstErrorText = "dst err"

    // spyk from MockK doesn't work well with suspend functions.
    val src = run {
      val originalSrc = ByteArrayInputStream(data).consumeAsEelChannel()
      object : EelReceiveChannel by originalSrc {
        override suspend fun receive(dst: ByteBuffer): ReadResult {
          if (srcErr) {
            throw IOException(srcErrorText)
          }
          return originalSrc.receive(dst)
        }
      }
    }

    val dst = run {
      val originalDst = ByteArrayOutputStream().asEelChannel()
      object : EelSendChannel by originalDst {
        override suspend fun send(src: ByteBuffer) {
          if (dstErr) {
            throw IOException(dstErrorText)
          }
          originalDst.send(src)
        }
      }
    }

    // Due to mokk bug `Default` can't be used
    try {
      copy(src, dst, dispatcher = Dispatchers.IO)
      assertTrue(!srcErr && !dstErr, "Error should be returned")
    } catch (e: CopyError) {
      when (e) {
        is CopyError.InError -> {
          assertTrue(srcErr, "Unexpected src error")
          assertEquals(srcErrorText, e.cause.message, "Wrong src error")
        }
        is CopyError.OutError -> {
          assertTrue(dstErr, "Unexpected dst error")
          assertEquals(dstErrorText, e.cause.message, "Wrong dst error")
        }
      }
    }
  }

  @CartesianTest
  fun testErrorWithContinue(@CartesianTest.Values(booleans = [true, false]) dstError: Boolean) = timeoutRunBlocking {
    val limit = 10
    val src = object : EelReceiveChannel {
      var error = false
      var counter = limit
      override suspend fun receive(dst: ByteBuffer): ReadResult {
        error = !error
        if (error && !dstError) {
          throw SocketTimeoutException("wait")
        }
        if (counter < 0) {
          return EOF
        }
        dst.put(counter.toByte())
        counter -= 1
        return NOT_EOF
      }

      override fun available(): Int {
        TODO("Not yet implemented")
      }

      override suspend fun closeForReceive() = Unit

      override val prefersDirectBuffers: Boolean = false
    }

    val result = mutableListOf<Byte>()
    val dst = object : EelSendChannel {
      override val isClosed: Boolean = false
      var error = false

      @Suppress("OPT_IN_OVERRIDE")
      override suspend fun send(dst: ByteBuffer) {
        error = !error
        if (error && dstError) throw SocketTimeoutException("wait")
        result.add(dst.get())
      }

      override suspend fun close(err: Throwable?) = Unit

      override val prefersDirectBuffers: Boolean = false
    }

    var errorHappened = false
    copy(src, dst, onReadError = {
      assertEquals(SocketTimeoutException::class, it::class)
      errorHappened = true
      OnError.RETRY
    }, onWriteError = {
      assertEquals(SocketTimeoutException::class, it::class)
      errorHappened = true
      OnError.RETRY
    })

    assertTrue(errorHappened, "No error callback called")
    assertArrayEquals((0..limit).map { it.toByte() }.reversed().toTypedArray(), result.toTypedArray(), "wrong data copied")
  }

  @Test
  fun testStreamNeverReturnsZero(): Unit = timeoutRunBlocking {
    var byteCounter = 2
    val channel = object : EelReceiveChannel {

      override suspend fun receive(dst: ByteBuffer): ReadResult {
        when {
          byteCounter > 0 -> {
            dst.put(42)
          }
          byteCounter == 0 -> Unit
          byteCounter < 0 -> {
            return EOF
          }
        }
        byteCounter -= 1
        return NOT_EOF
      }

      override fun available(): Int {
        TODO("Not yet implemented")
      }

      override suspend fun closeForReceive() = Unit

      override val prefersDirectBuffers: Boolean = false
    }
    val stream = channel.consumeAsInputStream()
    while (true) {
      val buffer = ByteArray(10)
      val bytesRead = stream.read(buffer)
      if (bytesRead == -1) break
      Assertions.assertEquals(42, buffer[0], "Only 42 must be read")
    }
  }

  @Test
  fun flushChannelTest(): Unit = timeoutRunBlocking {
    val inputStream = mock<OutputStream>(OutputStream::class.java)
    expect(inputStream.write(anyObject(), anyInt(), anyInt()))
    expect(inputStream.flush())
    replay(inputStream)
    inputStream.asEelChannel().send(allocate(42))
    verify(inputStream)
  }

  @Test
  fun testStreamAvailable(): Unit = timeoutRunBlocking {
    val bytesCount = 8192
    val pipe = EelPipe(prefersDirectBuffers = false)
    val input = pipe.source.consumeAsInputStream()
    Assertions.assertEquals(0, input.available(), "empty stream must have 0 available")

    // 8192
    launch {
      pipe.sink.sendWholeBuffer(allocate(bytesCount))
    }
    awaitForCondition {
      Assertions.assertEquals(bytesCount, input.available(), "Wrong number of bytes available")
    } // 8192
    pipe.source.receive(allocate(bytesCount)) // 0
    launch {
      pipe.sink.sendWholeBuffer(allocate(1))
    }
    awaitForCondition {
      Assertions.assertEquals(1, input.available(), "Wrong number of bytes available")
    } // 1

    //0
    pipe.source.receive(allocate(bytesCount))
    awaitForCondition {
      Assertions.assertEquals(0, input.available(), "After receiving there must be 0 bytes")
    }
    launch {
      try {
        pipe.sink.sendWholeBuffer(allocate(bytesCount))
        fail("Writing to the closed channel must fail")
      }
      catch (e: IOException) {
        // Expected exception
      }
    }
    awaitForCondition {
      Assertions.assertEquals(bytesCount, input.available(), "Wrong number of bytes available")
    }
    pipe.closePipe(null)
    awaitForCondition {
      Assertions.assertEquals(0, input.available(), "Closed channel available must be 0 bytes")
    }
  }

  @CartesianTest
  fun testCopyEelChannel(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
    @IntRangeSource(from = 1, to = TEXT.length + 1) destBlockSize: Int,
    @CartesianTest.Values(ints = [1, 2, 6, TEXT.length, 8192]) bufferSize: Int,
    @CartesianTest.Values(booleans = [true, false]) useNioChannel: Boolean,
  ): Unit = timeoutRunBlocking(30.seconds) {
    val src = ByteArrayInputStreamLimited(data, srcBlockSize)
    val dst = ByteArrayChannelLimited(destBlockSize)
    if (useNioChannel) {
      copy(Channels.newChannel(src).consumeAsEelChannel(), dst.asEelChannel(), bufferSize)
    }
    else {
      copy(src.consumeAsEelChannel(), dst.asEelChannel(), bufferSize)
    }
    assertEquals(TEXT, dst.toByteArray().decodeToString())
  }

  @CartesianTest
  fun testPipe(
    @IntRangeSource(from = 1, to = (TEXT.length + 1) * 2) blockSize: Int,
  ): Unit = timeoutRunBlocking(30.seconds) {
    val repeatText = 3
    val result = allocate(8192)
    val pipe = EelPipe(prefersDirectBuffers = false)


    async {
      val input = pipe.sink
      repeat(repeatText) {
        input.sendWholeBuffer(wrap(data))
      }
      assertFalse(input.isClosed)
      input.close(null)
      assertTrue(input.isClosed)
    }

    val buffer = allocate(blockSize)
    while (pipe.source.receive(buffer) != EOF) {
      if (!buffer.hasRemaining()) {
        result.put(buffer.flip())
        buffer.clear()
      }
    }
    if (buffer.position() != 0) {
      result.put(buffer.flip())
    }

    assertEquals(TEXT.repeat(3), result.flip().decodeString())
  }


  @Test
  fun outputChannelTest(): Unit = timeoutRunBlocking {
    val outputStream = ByteArrayOutputStream(data.size)
    val eelChannel = outputStream.asEelChannel()
    eelChannel.send(wrap(data))
    assertEquals(TEXT, outputStream.toByteArray().decodeToString())
  }

  @CartesianTest
  fun inputChannelTest(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ): Unit = timeoutRunBlocking {
    val inputStream = ByteArrayInputStreamLimited(data, srcBlockSize)
    val eelChannel = inputStream.consumeAsEelChannel()
    val buffer = allocate(data.size)
    while (buffer.hasRemaining() && eelChannel.receive(buffer) != EOF) {
    }
    val textFromChannel = buffer.rewind().decodeString()
    assertEquals(TEXT, textFromChannel)
  }

  @CartesianTest
  fun testReadAllBytes(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ): Unit = timeoutRunBlocking {
    val eelChannel = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel()
    val result = eelChannel.readAllBytes().decodeToString()
    assertEquals(TEXT, result)
    assertEquals(0, eelChannel.readAllBytes().size)
  }

  @CartesianTest
  fun testEmptyRead(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ): Unit = timeoutRunBlocking {
    val eelChannel = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel()
    assertEquals(NOT_EOF, eelChannel.receive(allocate(0)))
    assertEquals(0, eelChannel.consumeAsInputStream().read(ByteArray(data.size), data.size, 0))
  }

  @Nested
  inner class TestEelOutputChannel {

    @Test
    fun testSendWholeBuffer(): Unit = timeoutRunBlocking {
      val pipe = EelOutputChannel(false)
      val producerProgress = AtomicInteger(0)
      val chunksCount = 50
      coroutineScope {
        val producer = launch {
          for (i in 0 until chunksCount) {
            pipe.sendWholeBuffer(wrap(i.toString().plus("\n").toByteArray()))
            producerProgress.set(i)
          }
          pipe.sendEof()
        }
        val consumer = launch {
          val readBuffer = allocate(10)
          var i = 0
          do {
            readBuffer.clear()
            val readResult = pipe.exposedSource.receive(readBuffer)
            if (readResult == EOF) {
              break
            }
            readBuffer.flip()
            val readLine = readBuffer.moveToByteArray().toString(Charsets.UTF_8).removeSuffix("\n").toInt()
            assertEquals(i, readLine)
            assertTrue((readLine - producerProgress.get()).absoluteValue <= 1)
            i += 1
          }
          while (true)
          assertEquals(chunksCount, i)
        }
      }
    }

    @Test
    fun testSendUntilEndNoInterrupt(): Unit = timeoutRunBlocking {
      testSendUntilEnd(chunksCount = 100)
    }

    @Test
    fun testSendUntilEndInterrupt(): Unit = timeoutRunBlocking {
      testSendUntilEnd(chunksCount = 100, endAtChunk = 50)
    }

    suspend fun testSendUntilEnd(chunksCount: Int, endAtChunk: Int? = null) {
      val pipe = EelOutputChannel(false)
      val producerProgress = AtomicInteger(0)
      coroutineScope {
        val processExited = CompletableDeferred<Unit>()
        launch {
          pipe.sendUntilEnd(flow {
            for (i in 0 until chunksCount) {
              emit(i.toString().plus("\n").toByteArray())
              producerProgress.set(i)
            }
          }, processExited)
        }
        val readBuffer = allocate(10 * chunksCount)
        var i = 0
        do {
          val expectedRead = if (i == endAtChunk) {
            processExited.complete(Unit)
            waitUntil { producerProgress.get() == chunksCount - 1 }
            (i until chunksCount).joinToString("") { "$it\n" }.also {
              i = chunksCount
            }
          }
          else {
            assertTrue { (producerProgress.get() - i).absoluteValue <= 1 }
            "$i\n".also {
              i += 1
            }
          }
          val readResult = pipe.exposedSource.receive(readBuffer)
          if (readResult == EOF) {
            break
          }
          readBuffer.flip()
          val readLine = readBuffer.moveToByteArray().toString(Charsets.UTF_8)
          assertEquals(expectedRead, readLine)
          readBuffer.clear()
        }
        while (true)
        assertEquals(chunksCount + 1, i)
      }
    }

    @Test
    fun testPipeWithErrorClosedForReceive(): Unit = timeoutRunBlocking {
      val pipe = EelOutputChannel(false)
      pipe.exposedSource.closeForReceive()
      try {
        pipe.sendWholeBuffer(ByteBuffer.wrap("D".toByteArray()))
        fail("Writing into closed channel must be an error")
      }
      catch (e: EelChannelClosedException) {
        // Expected exception
      }
    }

    @Test
    fun testPipeWithErrorException(): Unit = timeoutRunBlocking {
      val pipe = EelOutputChannel(false)

      val error = Exception("some error")
      val expectedMessageError = "Pipe was broken with message: ${error.message}"

      pipe.ensureClosed(error)
      try {
        pipe.sendWholeBuffer(ByteBuffer.wrap("D".toByteArray()))
        fail("Writing into broken pipe must be an error")
      }
      catch (e: EelChannelClosedException) {
        assertEquals(error, e.cause)
      }

      try {
        pipe.exposedSource.receive(allocate(1))
        fail("Reading from broken pipe must be an error")
      }
      catch (e: EelChannelClosedException) {
        assertEquals(error, e.cause)
      }
    }

  }

  @Test
  fun testPipeWithErrorClosed(): Unit = timeoutRunBlocking {
    val pipe = EelPipe(prefersDirectBuffers = false)
    pipe.source.closeForReceive()
    try {
      pipe.sink.send(ByteBuffer.wrap("D".toByteArray()))
      fail("Writing into closed channel must be an error")
    }
    catch (e: IOException) {
      // Expected exception
    }
  }

  @Test
  fun testPipeWithErrorException(): Unit = timeoutRunBlocking {
    val pipe = EelPipe(prefersDirectBuffers = false)

    val error = Exception("some error")
    val expectedMessageError = "Pipe was broken with message: ${error.message}"

    pipe.closePipe(error)
    try {
      pipe.sink.send(ByteBuffer.wrap("D".toByteArray()))
      fail("Writing into broken pipe must be an error")
    }
    catch (e: IOException) {
      assertEquals(expectedMessageError, e.message)
    }

    try {
      pipe.source.receive(allocate(1))
      fail("Reading from broken pipe must be an error")
    }
    catch (e: IOException) {
      assertEquals(expectedMessageError, e.message)
    }
  }

  @Test
  fun testPipeWithSeveralOutputs(): Unit = timeoutRunBlocking {
    val lettersSent: MutableCollection<Char> = ConcurrentLinkedDeque<Char>()
    val pipe = EelPipe(prefersDirectBuffers = false)
    val sendJob1 = launch {
      for (c in 'a'..'z') {
        pipe.sink.sendWholeText("$c")
        lettersSent.add(c)
      }
    }
    val sendJob2 = launch {
      for (c in 'A'..'Z') {
        pipe.sink.sendWholeText("$c")
        lettersSent.add(c)
      }
    }
    val readJob = async {
      pipe.source.readWholeText()
    }
    assertFalse(pipe.sink.isClosed)
    sendJob1.join()
    sendJob2.join()
    pipe.sink.close(null)
    assertTrue(pipe.sink.isClosed)
    val text = readJob.await()
    assertThat("Some litters missing", text.toCharArray().toList(), containsInAnyOrder(*lettersSent.toTypedArray()))

  }

  @CartesianTest
  fun testKotlinChannel(
    @IntRangeSource(from = 1, to = TEXT.length + 1) srcBlockSize: Int,
  ): Unit = timeoutRunBlocking {
    val eelChannel = ByteArrayInputStreamLimited(data, srcBlockSize).consumeAsEelChannel()
    val kotlinChannel = consumeReceiveChannelAsKotlin(eelChannel)
    val result = ByteArrayOutputStream()
    for (buffer in kotlinChannel) {
      val data = ByteArray(buffer.remaining())
      buffer.get(data)
      result.writeBytes(data)
    }
    assertEquals(TEXT, result.toString())
  }

  @Test
  fun testKotlinChannelWithError(
  ): Unit = timeoutRunBlocking {
    val brokenChannel = mockk<EelReceiveChannel>()
    val error = IOException("go away me busy")
    coEvery { brokenChannel.receive(any()) } answers { throw error }
    every { brokenChannel.prefersDirectBuffers } returns false
    try {
      consumeReceiveChannelAsKotlin(brokenChannel).receive()
    }
    catch (e: IOException) {
      assertEquals(error.toString(), e.toString())
      return@timeoutRunBlocking
    }
    fail("No exception was thrown, also channel returned an error")
  }

  @CartesianTest
  fun testLines(
    @IntRangeSource(from = 1, to = 10) blockSize: Int,
    @CartesianTest.Values(strings = ["\n", "\r\n"]) separator: String,
  ): Unit = timeoutRunBlocking {
    val lines = buildList {
      add("`Twas brillig, and the slithy toves")
      add("Did gyre and gimble in the wabe")
      add("All mimsy were the borogoves,")
      add("And the mome raths outgrabe")
    }
    val eelChannel = ByteArrayInputStreamLimited(lines.joinToString(separator).encodeToByteArray(), blockSize).consumeAsEelChannel()
    val result = mutableListOf<String>()
    eelChannel.lines().collect { line ->
      result.add(line.trim())
    }
    Assertions.assertArrayEquals(lines.toTypedArray(), result.toTypedArray(), "Wrong lines collected")
  }
}

/**
 * Writes bytes nor more than [blockSize] at once, get data with [toByteArray]
 */
private class ByteArrayChannelLimited(private val blockSize: Int) : WritableByteChannel {
  private val byteArrayOutputStream = ByteArrayOutputStream()

  @Volatile
  private var closed = false
  override fun write(src: ByteBuffer): Int {
    val bytesToCopy = min((src.limit() - src.position()), blockSize)
    val tmpBuffer = ByteArray(bytesToCopy)
    src.get(tmpBuffer)
    byteArrayOutputStream.writeBytes(tmpBuffer)
    return bytesToCopy
  }

  override fun isOpen(): Boolean = !closed

  override fun close() {
    byteArrayOutputStream.close()
    closed = true
  }

  fun toByteArray(): ByteArray = byteArrayOutputStream.toByteArray()
}

/**
 * Returns [data] no more than [blockSize] at once
 */
private class ByteArrayInputStreamLimited(data: ByteArray, private val blockSize: Int) : InputStream() {
  private val iterator = data.iterator()
  override fun read(): Int = if (iterator.hasNext()) iterator.next().toInt() else -1
  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (!iterator.hasNext()) return -1
    val bytesToWrite = min(len, blockSize)
    var i = 0
    while (iterator.hasNext() && i < bytesToWrite) {
      b[off + i] = iterator.next()
      i++
    }
    return i
  }
}

private suspend fun awaitForCondition(code: suspend () -> Unit) {
  repeat(10) {
    try {
      code()
    }
    catch (_: AssertionFailedError) {
      delay(100)
    }
  }
  code()
}
