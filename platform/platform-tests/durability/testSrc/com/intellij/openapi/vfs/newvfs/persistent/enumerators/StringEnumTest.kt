// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.enumerators

import com.intellij.openapi.vfs.newvfs.persistent.*
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DataEnumeratorEx
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


/**
 * This should be just [DataEnumerator], but we create dedicated interface to slightly adjust the
 * contract -- e.g. nullability -- and also just to have more control over it
 */
internal interface StringEnum {
  fun tryEnumerate(s: String): Int
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
    data class TryEnumerate(val s: String) : Request

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
    data class Failure(val message: String) : Response

    @Serializable
    data class Enumerated(val idx: Int) : Response

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

internal class StringEnumeratorAppHelper(private val enumeratorBackend: StringEnum) {
  fun run(appAgent: AppAgent) {
    while (true) {
      try {
        when (val req = appAgent.input.readProto() as? Proto.Request) {
          is Proto.Request.Flush -> {
            enumeratorBackend.flush()
            appAgent.output.writeProto(Proto.Response.Ok)
          }
          is Proto.Request.Close -> {
            enumeratorBackend.close()
            appAgent.output.writeProto(Proto.Response.Ok)
          }
          is Proto.Request.Enumerate -> {
            val id = enumeratorBackend.enumerate(req.s)
            appAgent.output.writeProto(Proto.Response.Enumerated(id))
          }
          is Proto.Request.TryEnumerate -> {
            val id = enumeratorBackend.tryEnumerate(req.s)
            appAgent.output.writeProto(Proto.Response.Enumerated(id))
          }
          is Proto.Request.ValueOf -> {
            val s = enumeratorBackend.valueOf(req.idx)
            appAgent.output.writeProto(Proto.Response.ValueOf(s))
          }
          null -> { // connection closed
            enumeratorBackend.close()
            break
          }
        }
      }
      catch (e: Throwable) {
        appAgent.output.writeProto(Proto.Response.Failure(e.message ?: e.javaClass.name))
        throw e
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
      check(result is Proto.Response.Enumerated) { "$result" }
      return result.idx
    }

    fun tryEnumerate(s: String, afterRequest: () -> Unit = {}): Int {
      appController.appInput.writeProto(Proto.Request.TryEnumerate(s))
      afterRequest()
      val result = appController.appOutput.readProto()
      check(result is Proto.Response.Enumerated) { "$result" }
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

      fun randomEnumeratedString(): String = forward.values.random(random)
    }

    val enumerateRandomNewString: API.(() -> Unit) -> Unit = { afterRequest ->
      check(state.unconfirmedCommitted == null)
      val str = state.newRandomString()
      state.unconfirmedCommitted = str
      //println("enumerate $str")
      val id = enumerate(str, afterRequest)
      //println("enumerate $str -> $id")
      check(id > state.lastSeenId) {
        "Expect new id from enumerate(new string), but got $id <= lastSeenId: ${state.lastSeenId} (new string: '$str')"
      }
      state.lastSeenId = id
      state.forward[id] = str
      state.inverse[str] = id
      state.unconfirmedCommitted = null
    }

    val tryEnumerateRandomKnownString: API.(() -> Unit) -> Unit = {
      if (!state.forward.isEmpty()) {
        val knownString = state.randomEnumeratedString()
        val expectedId = state.inverse[knownString]
        val id = tryEnumerate(knownString)
        check(expectedId == id) { "tryEnumerate('$knownString') must be $expectedId, but returns $id instead" }
      }
    }

    val valueOfRandomKnownId: API.(() -> Unit) -> Unit = { afterRequest ->
      check(state.unconfirmedCommitted == null)
      val forwardKeys = state.forward.keys.toList()
      if (!forwardKeys.isEmpty()) {
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
        userAgent.runApplication { app ->
          val api = API(app)

          //check the state (initial/after kill):
          for (id in state.forward.keys) {
            val expectedStr = state.forward[id]!!
            check(state.inverse[expectedStr] == id) {
              "Bug: state self-check failed: forward[$id]='$expectedStr', while inverse[$expectedStr]=${state.inverse[expectedStr]}"
            }
            val actualStr = api.valueOf(id)
            if (actualStr != expectedStr) {
              userAgent.addInteractionResult(
                InteractionResult(false, "state mismatch (valueOf)", "valueOf($id): expected='$expectedStr', got='$actualStr'")
              )
              foundFail = true
              return@runApplication
            }
            val actualId = api.tryEnumerate(expectedStr)
            if (actualId != id) {
              userAgent.addInteractionResult(
                InteractionResult(false, "state mismatch (tryEnumerate)", "tryEnumerate('$expectedStr'): expected id=$id, got=$actualId")
              )
              foundFail = true
              return@runApplication
            }
          }

          val unconfirmedString = state.unconfirmedCommitted
          if (unconfirmedString != null) {
            val unconfirmedIdOrZero = api.tryEnumerate(unconfirmedString)
            if (unconfirmedIdOrZero != DataEnumeratorEx.NULL_ID) {
              check(state.lastSeenId < unconfirmedIdOrZero) {
                "Last (unconfirmed) enumerate($unconfirmedString) got id($unconfirmedString) < lastSeenId(${state.lastSeenId})"
              }
              val unconfirmedIdResolvedBack = api.valueOf(unconfirmedIdOrZero)
              if (unconfirmedIdResolvedBack == unconfirmedString) {
                state.lastSeenId = unconfirmedIdOrZero
                state.forward[unconfirmedIdOrZero] = unconfirmedString
                state.inverse[unconfirmedString] = unconfirmedIdOrZero
                userAgent.addInteractionResult(
                  InteractionResult(true, "state match (unconfirmed .enumerate() found to be committed)")
                )
              }
              else {
                userAgent.addInteractionResult(
                  InteractionResult(false, "state mismatch (unconfirmed .enumerate() found to be wrongly committed)",
                                    "Unconfirmed .enumerate(${unconfirmedString}) = $unconfirmedIdOrZero, but .valueOf($unconfirmedIdOrZero)='$unconfirmedIdResolvedBack' (lastSeenId=${state.lastSeenId})")
                )
                foundFail = true
                return@runApplication
              }
            } // else: unconfirmed committed wasn't really commited -> it's OK
            state.unconfirmedCommitted = null
          }

          //first fill enumerator up with _some_ initial data:
          val requestsBeforeKill = random.nextInt(0, 200)
          repeat(requestsBeforeKill) {
            api.randomRequest(
              afterRequest = {},
              enumerateRandomNewString, enumerateRandomNewString, enumerateRandomNewString, enumerateRandomNewString, // =4/6
              tryEnumerateRandomKnownString,  // =1/6
              valueOfRandomKnownId            // =1/6
            )
          }

          val killerJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val delayNs = random.nextLong(25.milliseconds.inWholeNanoseconds)
            LockSupport.parkNanos(delayNs) // delay from coroutines works in milliseconds only
            app.kill()
          }
          try {
            //Now _kill_ the App at some random moment, while _continuing_ to fill it with more data:
            while (true) {
              api.randomRequest(
                afterRequest = {
                  if (random.nextInt(5) == 0) killerJob.start()
                },
                enumerateRandomNewString, valueOfRandomKnownId
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