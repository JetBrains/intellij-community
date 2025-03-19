// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.bytearraystorage

import com.intellij.openapi.vfs.newvfs.persistent.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration.Companion.milliseconds

internal interface Storage {
  fun setBytes(bytes: ByteArray, offset: Int)
  fun getBytes(offset: Int, size: Int): ByteArray
  fun flush()
  fun close()

  fun pageSize(): Int = PAGE_SIZE
  fun maxCapacity(): Int = MAX_CAPACITY

  companion object {
    val MAX_CAPACITY = (System.getenv("stress.max-storage-capacity") ?: "1000000").toInt()
    private val PAGE_SIZE = (System.getenv("stress.page-size") ?: "4096").toInt()
  }
}


@Serializable
private sealed interface Proto {
  @Serializable
  sealed interface Request : Proto {
    @Serializable
    data class SetBytes(val offset: Int, val bytes: ByteArray) : Request

    @Serializable
    data class ReadBytes(val offset: Int, val size: Int) : Request

    @Serializable
    object Flush : Request

    @Serializable
    object Close : Request
  }

  @Serializable
  sealed interface Response : Proto {
    @Serializable
    object Ok : Response

    @Serializable
    data class ReadBytes(val bytes: ByteArray) : Response
  }
}

private fun OutputStream.writeProto(obj: Proto) {
  val msg = Json.Default.encodeToString(Proto.serializer(), obj).encodeToByteArray()
  val sizeBuf = ByteBuffer.allocate(4)
  sizeBuf.putInt(msg.size)
  write(sizeBuf.array())
  write(msg)
  flush()
}

private fun InputStream.readProto(): Proto {
  val sizeArr = readNBytes(4)
  if (sizeArr.isEmpty()) throw EOFException("EOF") // EOF
  check(sizeArr.size == 4) { "sizeArr.size != 4, != 0" } // partial msg
  val size = ByteBuffer.wrap(sizeArr).getInt()
  val data = readNBytes(size)
  check(data.size == size) { "data.size != size" } // partial msg
  return Json.decodeFromString(data.decodeToString())
}

internal class StorageApp(private val storageBackend: Storage) : App {
  companion object {
    val ENFORCE_PER_PAGE_WRITES = (System.getenv("stress.enforce-per-page-writes") ?: "false").toBooleanStrict()
  }

  override fun run(appAgent: AppAgent) {
    try {
      while (true) {
        when (val req = appAgent.input.readProto() as Proto.Request) {
          is Proto.Request.Close -> {
            storageBackend.close()
            appAgent.output.writeProto(Proto.Response.Ok)
            break
          }
          is Proto.Request.ReadBytes -> {
            val bytes = storageBackend.getBytes(req.offset, req.size)
            appAgent.output.writeProto(Proto.Response.ReadBytes(bytes))
          }
          is Proto.Request.SetBytes -> {
            if (!ENFORCE_PER_PAGE_WRITES) {
              storageBackend.setBytes(req.bytes, req.offset)
            }
            else {
              val pageSize = storageBackend.pageSize()
              val firstPage = req.offset / pageSize
              val lastPage = (req.offset + req.bytes.size - 1) / pageSize
              for (page in firstPage..lastPage) {
                val begin = (page * pageSize).coerceAtLeast(req.offset)
                val end = ((page + 1) * pageSize).coerceAtMost(req.offset + req.bytes.size)
                storageBackend.setBytes(req.bytes.copyOfRange(begin - req.offset, end - req.offset), begin)
              }
            }
            appAgent.output.writeProto(Proto.Response.Ok)
          }
          is Proto.Request.Flush -> {
            storageBackend.flush()
            appAgent.output.writeProto(Proto.Response.Ok)
          }
        }
      }
    }
    catch (e: EOFException) {
      storageBackend.close()
      return
    }
  }
}

/**
 * Scenario tested:
 * 1. User does random get/set requests [0..20] times (kind of warmup)
 * 2. After that, user issues only set/flush requests..
 * 3. ...and kills the remote app at a random moment, in parallel
 * Scenario is repeated 10 times, at the beginning of each subsequent invocation we check the state of the storage to be
 * consistent with the requests applied before.
 * The very last request could be either applied or not (possibleValidState), but it should be either applied or not as
 * a whole -- i.e. we assume the requests are atomic 'transactions'.
 */
internal class StorageUser : User {
  class InvalidStateException(message: String) : Exception(message)

  class API(val appController: AppController) {
    fun get(offset: Int, size: Int, afterRequest: () -> Unit = {}): ByteArray {
      appController.appInput.writeProto(Proto.Request.ReadBytes(offset, size))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.ReadBytes) { "$result" }
      return result.bytes
    }

    fun set(offset: Int, data: ByteArray, afterRequest: () -> Unit = {}) {
      appController.appInput.writeProto(Proto.Request.SetBytes(offset, data))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.Ok) { "$result" }
    }

    fun flush(afterRequest: () -> Unit = {}) {
      appController.appInput.writeProto(Proto.Request.Flush)
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.Ok) { "$result" }
    }

    fun close(afterRequest: () -> Unit = {}) {
      appController.appInput.writeProto(Proto.Request.Close)
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.Ok) { "$result" }
    }
  }

  override fun run(userAgent: UserAgent) {
    val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val random = userAgent.random
    val timesToKill = 10
    val lastAcknowledgedState = ByteArray(Storage.MAX_CAPACITY)
    val possibleValidStateRef = AtomicReference<ByteArray?>()

    val randomGet: API.(() -> Unit) -> Unit = { afterRequest ->
      val offset = random.nextInt(0, Storage.MAX_CAPACITY)
      val size = random.nextInt(1, Storage.MAX_CAPACITY - offset + 1)
      val expected = lastAcknowledgedState.copyOfRange(offset, offset + size)
      val result = get(offset, size, afterRequest)
      if (!result.contentEquals(expected)) {
        throw InvalidStateException("readBytes offset=$offset size=$size: diff=${buildDiff(expected, result)}")
      }
    }

    val randomSet: API.(() -> Unit) -> Unit = { afterRequest ->
      val offset = random.nextInt(0, Storage.MAX_CAPACITY)
      val size = random.nextInt(1, Storage.MAX_CAPACITY - offset + 1)
      val data = random.nextBytes(size)
      val stateCopy = lastAcknowledgedState.copyOf()
      data.copyInto(stateCopy, offset)
      possibleValidStateRef.set(stateCopy)
      set(offset, data, afterRequest)
      data.copyInto(lastAcknowledgedState, offset)
      possibleValidStateRef.set(null)
    }

    fun API.randomRequest(afterRequest: () -> Unit, vararg options: API.(() -> Unit) -> Unit) {
      options[random.nextInt(0, options.size)].invoke(this, afterRequest)
    }

    var foundFail = false
    try {
      repeat(timesToKill + 1) { runCounter ->
        if (foundFail) return@repeat
        userAgent.runApplication { app ->
          val api = API(app)
          if (runCounter == 0) api.set(0, lastAcknowledgedState)

          val fullState = api.get(0, Storage.MAX_CAPACITY)
          if (!lastAcknowledgedState.contentEquals(fullState)) {
            val possibleState = possibleValidStateRef.get()
            if (possibleState != null) {
              if (possibleState.contentEquals(fullState)) {
                // commit was done, but we didn't see the acknowledgement
                fullState.copyInto(lastAcknowledgedState)
                userAgent.addInteractionResult(InteractionResult(true, "unconfirmed committed"))
              }
              else {
                userAgent.addInteractionResult(
                  InteractionResult(false, "state mismatch (with unconfirmed commit)",
                                    "full state check failed: diff with last acknowledged state=${
                                      buildDiff(lastAcknowledgedState, fullState)
                                    }, " +
                                    "diff with possible valid state=${buildDiff(possibleState, fullState)}, " +
                                    "overall: ${describeDiff(lastAcknowledgedState, "lastAcked", possibleState, "possibleNew", fullState)}")
                )
                foundFail = true
                return@runApplication
              }
            }
            else {
              userAgent.addInteractionResult(
                InteractionResult(false, "state mismatch",
                                  "full state check failed: diff with last acknowledged state=${
                                    buildDiff(lastAcknowledgedState, fullState)
                                  }")
              )
              foundFail = true
              return@runApplication
            }
          }
          else {
            if (runCounter > 0) userAgent.addInteractionResult(InteractionResult(true, "state match"))
          }
          possibleValidStateRef.set(null)

          val requestsBeforeKill = random.nextInt(0, 20)
          repeat(requestsBeforeKill) {
            api.randomRequest({}, randomSet, randomGet)
          }

          val killerJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val delayNs = random.nextLong(25.milliseconds.inWholeNanoseconds)
            LockSupport.parkNanos(delayNs) // delay from coroutines works in milliseconds only
            app.kill()
          }
          try {
            while (true) {
              api.randomRequest(
                {
                  if (random.nextInt(5) == 0) killerJob.start()
                },
                randomSet, randomSet, randomSet, randomSet, API::flush
              )
            }
          }
          catch (_: IOException) {
            //'normal' exit: remote app killed
          }
          catch (e: Throwable) {
            System.err.println("unexpected error after kill: ${e.message}")
            e.printStackTrace()
          }
        }
      }
    }
    catch (e: Throwable) {
      userAgent.addInteractionResult(InteractionResult(false, "unhandled exception", e.toString()))
      System.err.println("unexpected error: ${e.message}")
      e.printStackTrace()
    }
  }

  companion object {
    fun buildDiff(expected: ByteArray, actual: ByteArray): String {
      check(!expected.contentEquals(actual))
      return if (expected.size != actual.size) {
        "expected.size = ${expected.size}, actual.size = ${actual.size}"
      }
      else {
        var i = 0
        while (i < expected.size && expected[i] == actual[i]) i++
        var j = expected.size
        while (i < j && expected[j - 1] == actual[j - 1]) j--
        check(i != j)
        "$i..${j - 1}" // expected=${expected.copyOfRange(i, j).contentToString().take(32)}, actual=${actual.copyOfRange(i, j).contentToString().take(32) }
      }
    }

    fun describeDiff(expected1: ByteArray,
                     expected1Name: String,
                     expected2: ByteArray,
                     expected2Name: String,
                     actual: ByteArray): String {
      check(!expected1.contentEquals(actual))
      return if (expected1.size != actual.size) {
        "expected.size = ${expected1.size}, actual.size = ${actual.size}"
      }
      else {
        val sb = StringBuilder()
        var index = 0
        while (index < actual.size) {
          val nextMismatch1 = nextMismatch(expected1, actual, index)
          val nextMismatch2 = nextMismatch(expected2, actual, index)
          if (nextMismatch1 == -1) {
            sb.append("[$index..end)=${expected1Name}")
            break
          }
          else if (nextMismatch2 == -1) {
            sb.append("[$index..end)=${expected2Name}")
            break
          }

          if (nextMismatch1 > index || nextMismatch2 > index) {
            if (nextMismatch2 > nextMismatch1) {
              sb.append("[$index..${nextMismatch2})=${expected2Name}, ")
              index = nextMismatch2
            }
            else { //nextMismatch1 >= nextMismatch2
              sb.append("[$index..${nextMismatch1})=${expected1Name}, ")
              index = nextMismatch1
            }
          }
          else {
            index++
          }
        }

        return sb.toString()
      }
    }

    private fun nextMismatch(expected: ByteArray,
                             actual: ByteArray,
                             startingWith: Int): Int {
      for (mismatchIndex in startingWith..expected.size - 1) {
        if (expected[mismatchIndex] != actual[mismatchIndex]) {
          return mismatchIndex
        }
      }
      return -1
    }
  }
}