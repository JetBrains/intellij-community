// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.contentstorage

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.newvfs.persistent.App
import com.intellij.openapi.vfs.newvfs.persistent.AppAgent
import com.intellij.openapi.vfs.newvfs.persistent.AppController
import com.intellij.openapi.vfs.newvfs.persistent.InteractionResult
import com.intellij.openapi.vfs.newvfs.persistent.User
import com.intellij.openapi.vfs.newvfs.persistent.UserAgent
import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.CompressingAlgo.Lz4Algo
import com.intellij.openapi.vfs.newvfs.persistent.mapped.content.VFSContentStorageOverMMappedFile
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration.Companion.milliseconds

internal interface Storage {
  fun setBytes(bytes: ByteArray): Int
  fun getBytes(id: Int): ByteArray
  fun close()

  fun pageSize(): Int = PAGE_SIZE
  fun maxCapacity(): Int = MAX_CAPACITY

  companion object {
    val MAX_CAPACITY = (System.getenv("stress.max-storage-capacity") ?: "1000000").toInt()
    val PAGE_SIZE = (System.getenv("stress.page-size") ?: (1 shl 20).toString()).toInt()
  }
}


@Serializable
private sealed interface Proto {
  @Serializable
  sealed interface Request : Proto {
    @Serializable
    data class SetBytes(val bytes: ByteArray) : Request

    @Serializable
    data class ReadBytes(val id: Int) : Request

    @Serializable
    object Close : Request
  }

  @Serializable
  sealed interface Response : Proto {
    @Serializable
    object Ok : Response

    @Serializable
    data class BytesWritten(val id: Int) : Response

    @Serializable
    data class BytesRead(val bytes: ByteArray, val ok: Boolean) : Response
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
            try {
              val bytes = storageBackend.getBytes(req.id)
              appAgent.output.writeProto(Proto.Response.BytesRead(bytes, ok = true))
            }
            catch (t: Throwable) {
              appAgent.output.writeProto(Proto.Response.BytesRead(ByteArray(0), ok = false))
            }
          }
          is Proto.Request.SetBytes -> {
            val id = storageBackend.setBytes(req.bytes)
            appAgent.output.writeProto(Proto.Response.BytesWritten(id))
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
 * 2. After that, user issues only set requests..
 * 3. ...and kills the remote app at a random moment, in parallel
 * Scenario is repeated 10 times, at the beginning of each subsequent invocation we check the state of the storage to be
 * consistent with the requests applied before.
 * The very last request could be either applied or not (possibleValidState), but it should be either applied or not as
 * a whole -- i.e. we assume the requests are atomic 'transactions'.
 */
internal class StorageUser : User {
  class InvalidStateException(message: String) : Exception(message)

  class API(val appController: AppController) {
    fun get(id: Int, afterRequest: () -> Unit = {}): ByteArray {
      appController.appInput.writeProto(Proto.Request.ReadBytes(id))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.BytesRead) { "$result" }
      return result.bytes
    }

    fun set(data: ByteArray, afterRequest: () -> Unit = {}): Int {
      appController.appInput.writeProto(Proto.Request.SetBytes(data))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.BytesWritten) { "$result" }
      return result.id
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

    val storagePath = Path.of("user-${userAgent.id}.mmapped.data.goldensource")
    NioFiles.deleteRecursively(storagePath)
    val goldenSourceState = VFSContentStorageOverMMappedFile(storagePath, Storage.PAGE_SIZE, Lz4Algo(8000))
    goldenSourceState.use { goldenSourceState ->
      val allIds = IntOpenHashSet()
      val unfinishedWriteRef = AtomicReference<Pair<Int, ByteArray>>()

      val randomGet: API.(() -> Unit) -> Unit = { afterRequest ->
        if (!allIds.isEmpty()) {
          val index = random.nextInt(allIds.size)
          val id = allIds.toIntArray()[index]
          if (unfinishedWriteRef.get()?.first != id) {
            val expected = goldenSourceState.readStream(id).readAllBytes()
            val result = get(id)
            if (!result.contentEquals(expected)) {
              throw InvalidStateException("readBytes(id: $id) diff=${buildDiff(expected, result)}")
            }
          }
        }
      }

      val randomSet: API.(() -> Unit) -> Unit = { afterRequest ->
        val size = random.nextInt(1, Storage.PAGE_SIZE - 16 * 1024)
        val data = random.nextBytes(size)
        val goldenId = goldenSourceState.storeRecord(ByteArraySequence(data))
        unfinishedWriteRef.set(Pair(goldenId, data))
        val remoteId = set(data, afterRequest)
        check(remoteId == goldenId) { "localId(=$goldenId) != remoteId(=$remoteId)" }
        unfinishedWriteRef.set(null)
        allIds.add(goldenId)
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

            if( runCounter > 0 ) {
              //1) finish unfinished (to keep local/remote equivalence):
              val possiblyUnfinishedWrite = unfinishedWriteRef.get()
              if (possiblyUnfinishedWrite != null) {
                val remoteId = api.set(possiblyUnfinishedWrite.second)
                check(remoteId == possiblyUnfinishedWrite.first) { "localId(=${possiblyUnfinishedWrite.first}) != remoteId(=$remoteId)" }
              }

              //2) check remote state is equivalent to local:
              val it = goldenSourceState.createRecordIdIterator()
              while (it.hasNextId()) {
                val id = it.nextId()
                val goldenRecordBytes = goldenSourceState.readStream(id).readAllBytes()
                val remoteRecordBytes = api.get(id)
                if (!goldenRecordBytes.contentEquals(remoteRecordBytes)) {
                  userAgent.addInteractionResult(
                    InteractionResult(false, "state mismatch",
                                      "full state check failed: diff with last acknowledged state=${
                                        buildDiff(goldenRecordBytes, remoteRecordBytes)
                                      }")
                  )
                  foundFail = true
                  return@runApplication
                }
                else {
                  userAgent.addInteractionResult(InteractionResult(true, "state match"))
                }
              }
            }

            unfinishedWriteRef.set(null)

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
                  randomSet, randomSet, randomSet, randomSet
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

    fun describeDiff(
      expected1: ByteArray,
      expected1Name: String,
      expected2: ByteArray,
      expected2Name: String,
      actual: ByteArray,
    ): String {
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

    private fun nextMismatch(
      expected: ByteArray,
      actual: ByteArray,
      startingWith: Int,
    ): Int {
      for (mismatchIndex in startingWith..expected.size - 1) {
        if (expected[mismatchIndex] != actual[mismatchIndex]) {
          return mismatchIndex
        }
      }
      return -1
    }
  }
}