// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.platform.settings.v2.impl.SettingsDecodingException
import com.intellij.platform.settings.v2.impl.kotlinxSerializer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path

@Serializable
private data class RobustEndpoint(val host: String, val port: Int)

/**
 * Checks the fault isolation of a hand-edited config file. A value whose stored type does not match its key must not
 * break the rest of the file. Examples are a string for a number key, or a list for a scalar key. Every well-typed key
 * in the same layer resolves normally. The mistyped key falls back to its default, as if it had no value (ADR 0047).
 *
 * The layers come from committed JSON files that the production codec parses. Each file mixes good and bad entries. A
 * config whose top level is not an object is malformed as a whole (ADR 0145).
 */
@TestApplication
class SettingsJsonRobustnessTest {
  companion object {
    // scalarSwap.json: scalar keys whose stored value has the wrong scalar type, and their well-typed twins.
    private val strFromNum = SettingsKey("test.robust.strFromNum", default = "D")
    private val intFromStr = SettingsKey("test.robust.intFromStr", default = -1)
    private val boolFromStr = SettingsKey("test.robust.boolFromStr", default = false)
    private val goodStr = SettingsKey("test.robust.goodStr", default = "D")
    private val goodInt = SettingsKey("test.robust.goodInt", default = -1)
    private val goodBool = SettingsKey("test.robust.goodBool", default = false)

    // containerSwap.json: a scalar where a container is expected and back, and their well-typed twins.
    private val listFromScalar = SettingsKey("test.robust.listFromScalar", default = emptyList(), serializer = StringSerializer.listOf())
    private val scalarFromList = SettingsKey("test.robust.scalarFromList", default = -1)
    private val goodList = SettingsKey("test.robust.goodList", default = emptyList(), serializer = StringSerializer.listOf())
    private val goodScalar = SettingsKey("test.robust.goodScalar", default = -1)

    // objectMismatch.json: stored objects that do not match a `@Serializable` class, and their well-typed twin.
    private val defaultEndpoint = RobustEndpoint("D", -1)
    private val objWrongFieldType = SettingsKey("test.robust.objWrongFieldType", default = defaultEndpoint, serializer = kotlinxSerializer())
    private val objMissingField = SettingsKey("test.robust.objMissingField", default = defaultEndpoint, serializer = kotlinxSerializer())
    private val objFromScalar = SettingsKey("test.robust.objFromScalar", default = defaultEndpoint, serializer = kotlinxSerializer())
    private val goodObj = SettingsKey("test.robust.goodObj", default = defaultEndpoint, serializer = kotlinxSerializer())
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a mistyped scalar defaults while its well-typed siblings resolve`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    provider.seed(app, decode("scalarSwap.json"))

    awaitEquals("ok") { goodStr[app] }   // the file has landed once a good key reflects it
    assertEquals(5, goodInt[app])
    assertEquals(true, goodBool[app])

    assertEquals("D", strFromNum[app], "number stored for a string key → default")
    assertEquals(-1, intFromStr[app], "string stored for an int key → default")
    assertEquals(false, boolFromStr[app], "string stored for a boolean key → default")
  }

  @Test
  fun `a shape mismatch defaults while its well-typed siblings resolve`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    provider.seed(app, decode("containerSwap.json"))

    awaitEquals(listOf("a", "b")) { goodList[app] }
    assertEquals(9, goodScalar[app])

    assertEquals(emptyList<String>(), listFromScalar[app], "scalar stored for a list key → default")
    assertEquals(-1, scalarFromList[app], "list stored for a scalar key → default")
  }

  @Test
  fun `an object that does not match its serializable class defaults while its well-typed siblings resolve`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val app = SettingsContext.app()

    provider.seed(app, decode("objectMismatch.json"))

    awaitEquals(RobustEndpoint("ok", 8080)) { goodObj[app] }

    assertEquals(defaultEndpoint, objWrongFieldType[app], "string stored for an int field → default")
    assertEquals(defaultEndpoint, objMissingField[app], "required field absent → default")
    assertEquals(defaultEndpoint, objFromScalar[app], "scalar stored for an object key → default")
  }

  @Test
  fun `a config whose top level is not an object is malformed`() {
    for (name in listOf("topLevelArray.json", "topLevelNumber.json", "topLevelString.json")) {
      assertThrows<SettingsDecodingException>(name) { decode(name) }
    }
    assertEquals(MapValue(emptyMap()), JsonSettingsCodec.decode(" \n"))
  }

  private fun decode(name: String): MapValue =
    JsonSettingsCodec.decode(Files.readString(Path.of(testResourcePath("jsonRobustness/$name"))))
}
