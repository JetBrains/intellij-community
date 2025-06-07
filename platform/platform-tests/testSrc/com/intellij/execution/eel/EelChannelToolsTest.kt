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
import com.intellij.platform.eel.provider.utils.*
import com.intellij.testFramework.common.timeoutRunBlocking
import io.ktor.util.decodeString
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.easymock.EasyMock.*
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junitpioneer.jupiter.cartesian.CartesianTest
import org.junitpioneer.jupiter.params.IntRangeSource
import org.opentest4j.AssertionFailedError
import java.io.*
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteBuffer.allocate
import java.nio.ByteBuffer.wrap
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

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

    val src = spyk(ByteArrayInputStream(data).consumeAsEelChannel())
    coEvery { src.receive(any()) } answers {
      if (srcErr) {
        throw IOException(srcErrorText)
      }
      else callOriginal()
    }

    val dst = spyk(ByteArrayOutputStream().asEelChannel())
    coEvery {
      @Suppress("OPT_IN_USAGE") dst.send(any())
    } answers {
      if (dstErr) {
        throw IOException(dstErrorText)
      }
      else callOriginal()
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

      override suspend fun close() = Unit
    }

    val result = mutableListOf<Byte>()
    val dst = object : EelSendChannel {
      override val closed: Boolean = false
      var error = false

      @Suppress("OPT_IN_OVERRIDE")
      override suspend fun send(dst: ByteBuffer) {
        error = !error
        if (error && dstError) throw SocketTimeoutException("wait")
        result.add(dst.get())
      }

      override suspend fun close() = Unit
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

      override suspend fun close() = Unit
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
    val pipe = EelPipe()
    val input = pipe.source.consumeAsInputStream()
    Assertions.assertEquals(0, input.available(), "empty stream must have 0 available")

    // 8192
    launch {
      pipe.sink.sendWholeBuffer(allocate(bytesCount))
    } // 8192 * 2
    launch {
      pipe.sink.sendWholeBuffer(allocate(bytesCount))
    }
    awaitForCondition {
      Assertions.assertEquals(bytesCount * 2, input.available(), "Wrong number of bytes available")
    } // 8192
    pipe.source.receive(allocate(bytesCount)) // 8193
    launch {
      pipe.sink.sendWholeBuffer(allocate(1))
    }
    awaitForCondition {
      Assertions.assertEquals(bytesCount + 1, input.available(), "Wrong number of bytes available")
    } // 1
    pipe.source.receive(allocate(bytesCount))
    awaitForCondition {
      Assertions.assertEquals(1, input.available(), "After receiving there must be 0 bytes")
    }

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
    pipe.closePipe()
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
    val pipe = EelPipe()


    async {
      val input = pipe.sink
      repeat(repeatText) {
        input.sendWholeBuffer(wrap(data))
      }
      assertFalse(input.closed)
      input.close()
      assertTrue(input.closed)
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

  @Test
  fun testPipeWithErrorClosed(): Unit = timeoutRunBlocking {
    val pipe = EelPipe()
    pipe.source.close()
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
    val pipe = EelPipe()

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
    val pipe = EelPipe()
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
    assertFalse(pipe.sink.closed)
    sendJob1.join()
    sendJob2.join()
    pipe.sink.close()
    assertTrue(pipe.sink.closed)
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
