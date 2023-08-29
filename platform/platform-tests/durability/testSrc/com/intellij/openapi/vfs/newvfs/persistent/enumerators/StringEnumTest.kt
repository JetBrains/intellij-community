// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.enumerators

import com.intellij.openapi.vfs.newvfs.persistent.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.locks.LockSupport
import kotlin.random.nextInt
import kotlin.time.Duration.Companion.milliseconds


internal interface StringEnum {
  fun enumerate(s: String): Int
  fun valueOf(idx: Int): String?
  fun flush()
  fun close()
}

@Serializable
internal sealed interface Proto {
  @Serializable
  sealed interface Request : Proto {
    @Serializable
    data class Enumerate(val s: String) : Request

    @Serializable
    data class ValueOf(val idx: Int) : Request

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
    data class Enumerate(val idx: Int) : Response

    @Serializable
    data class ValueOf(val s: String?) : Response
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
  if (sizeArr.isEmpty()) throw IOException("EOF")
  if (sizeArr.size != 4) throw IOException("sizeArr.size != 4, != 0") // partial msg
  val size = ByteBuffer.wrap(sizeArr).getInt()
  val data = readNBytes(size)
  if (data.size != size) throw IOException("data.size != size") // partial msg
  return Json.decodeFromString(data.decodeToString())
}

internal class StringEnumApp(private val enumBackend: StringEnum) : App {
  override fun run(appAgent: AppAgent) {
    while (true) {
      when (val req = appAgent.input.readProto() as? Proto.Request) {
        is Proto.Request.Flush -> {
          enumBackend.flush()
          appAgent.output.writeProto(Proto.Response.Ok)
        }
        is Proto.Request.Close -> {
          enumBackend.close()
          appAgent.output.writeProto(Proto.Response.Ok)
        }
        is Proto.Request.Enumerate -> {
          val id = enumBackend.enumerate(req.s)
          appAgent.output.writeProto(Proto.Response.Enumerate(id))
        }
        is Proto.Request.ValueOf -> {
          val s = enumBackend.valueOf(req.idx)
          appAgent.output.writeProto(Proto.Response.ValueOf(s))
        }
        null -> { // connection closed
          enumBackend.close()
          break
        }
      }
    }
  }
}

internal class StringEnumUser : User {
  class API(val appController: AppController) {
    fun enumerate(s: String, afterRequest: () -> Unit = {}): Int {
      appController.appInput.writeProto(Proto.Request.Enumerate(s))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.Enumerate) { "$result" }
      return result.idx
    }

    fun valueOf(idx: Int, afterRequest: () -> Unit = {}): String? {
      appController.appInput.writeProto(Proto.Request.ValueOf(idx))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.ValueOf) { "$result" }
      return result.s
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

    val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    fun randomString(): String {
      return buildString {
        repeat(random.nextInt(1..100)) {
          append(alphabet[random.nextInt(alphabet.size)])
        }
      }
    }

    val state = object {
      var lastSeenId = 0
      val forward = HashMap<Int, String>()
      val inverse = HashMap<String, Int>()
      var unconfirmedCommitted: String? = null

      fun newRandomString(): String {
        while (true) {
          val s = randomString()
          if (s !in inverse && s != unconfirmedCommitted) return s
        }
      }
    }

    val randomEnumerateNew: API.(() -> Unit) -> Unit = { afterRequest ->
      check(state.unconfirmedCommitted == null)
      val str = state.newRandomString()
      state.unconfirmedCommitted = str
      //println("enumerate $str")
      val id = enumerate(str, afterRequest)
      //println("enumerate $str -> $id")
      check(id > state.lastSeenId) { "Expect new id from enumerate(new string), but got $id <= lastSeenId: ${state.lastSeenId} (new string: '$str')" }
      state.lastSeenId = id
      state.forward[id] = str
      state.inverse[str] = id
      state.unconfirmedCommitted = null
    }

    val randomValueOfExisting: API.(() -> Unit) -> Unit = { afterRequest ->
      check(state.unconfirmedCommitted == null)
      val forwardKeys = state.forward.keys.toList()
      if(!forwardKeys.isEmpty()) {
        val id = forwardKeys.let { it[random.nextInt(it.size)] }
        val expectedStr = state.forward[id]!!
        val actualStr = valueOf(id, afterRequest)
        check(actualStr == expectedStr) { "valueOf($id): expected '$expectedStr' != actual '$actualStr'" }
      }
    }

    fun API.randomRequest(afterRequest: () -> Unit, vararg options: API.(() -> Unit) -> Unit) {
      options[random.nextInt(0, options.size)].invoke(this, afterRequest)
    }

    var foundFail = false
    try {
      repeat(timesToKill + 1) { runCounter ->
        if (foundFail) return@repeat
        val requestsBeforeKill = random.nextInt(0, 200)
        userAgent.runApplication { app ->
          val api = API(app)

          for (id in state.forward.keys) {
            val expected = state.forward[id]!!
            check(state.inverse[expected] == id)
            val actualStr = api.valueOf(id)
            if (actualStr != expected) {
              userAgent.addInteractionResult(
                InteractionResult(false, "state mismatch (string)", "id=$id, expected=$expected, got=$actualStr")
              )
              foundFail = true
              return@runApplication
            }
            val actualId = api.enumerate(expected)
            if (actualId != id) {
              userAgent.addInteractionResult(
                InteractionResult(false, "state mismatch (id)", "string=$expected, expected id=$id, got=$actualId")
              )
              foundFail = true
              return@runApplication
            }
          }

          var unconfirmedCommittedIsOk = true
          var unconfirmedCommittedWitness: String? = null

          if (state.unconfirmedCommitted != null) {
            for (possibleId in (state.lastSeenId + 1)..(state.lastSeenId + 150)) {
              val valueOf = api.valueOf(possibleId)
              if (valueOf != null) {
                if (valueOf == state.unconfirmedCommitted) {
                  unconfirmedCommittedIsOk = true
                  unconfirmedCommittedWitness = valueOf
                  state.lastSeenId = possibleId
                  state.unconfirmedCommitted = null
                  state.forward[possibleId] = valueOf
                  state.inverse[valueOf] = possibleId
                }
                else {
                  unconfirmedCommittedIsOk = false
                  unconfirmedCommittedWitness = valueOf
                }
                break
              }
            }
          }
          else {
            val nextValueOf = api.valueOf(state.lastSeenId + 1)
            if (nextValueOf != null) {
              unconfirmedCommittedIsOk = false
              unconfirmedCommittedWitness = nextValueOf
            }
          }

          if (!unconfirmedCommittedIsOk) {
            userAgent.addInteractionResult(
              InteractionResult(false, "state mismatch (unconfirmed committed)",
                                "unconfirmed committed=${state.unconfirmedCommitted}, nextExpectedId=${state.lastSeenId}, got=$unconfirmedCommittedWitness")
            )
            foundFail = true
            return@runApplication
          }
          else {
            userAgent.addInteractionResult(
              InteractionResult(true, "state match" + if (state.unconfirmedCommitted != null) " (unconfirmed committed)" else "")
            )
            state.unconfirmedCommitted = null
          }

          repeat(requestsBeforeKill) {
            api.randomRequest(
              afterRequest = {},
              randomEnumerateNew, randomEnumerateNew, randomEnumerateNew, randomEnumerateNew, // 80%
              randomValueOfExisting // 20%
            )
          }

          val killerJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val delayNs = random.nextLong(25.milliseconds.inWholeNanoseconds)
            LockSupport.parkNanos(delayNs) // delay from coroutines works in milliseconds only
            app.kill()
          }
          try {
            while (true) {
              api.randomRequest(
                afterRequest = {
                  if (random.nextInt(5) == 0) killerJob.start()
                },
                randomEnumerateNew, randomValueOfExisting
              )
            }
          }
          catch (_: IOException) {
          } // EOF from requests
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