// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Checks the contract of [Serializer.deserialize] for an incorrect stored value. A serializer throws
 * [IllegalArgumentException], and the read falls back to the default with a warning.
 */
@TestApplication
class SettingsMalformedValueTest {
  companion object {
    private val marker = SettingsKey("test.malformed.marker", default = "")
    private val scalar = SettingsKey("test.malformed.scalar", default = -1)
    private val listElement = SettingsKey("test.malformed.listElement", default = listOf("d"), serializer = StringSerializer.listOf())
    private val mapValue = SettingsKey("test.malformed.mapValue", default = mapOf("k" to "d"), serializer = StringSerializer.mapOf())
    private val nullableScalar = SettingsKey("test.malformed.nullable", default = -1, serializer = IntSerializer.nullable())
    private val rejecting = SettingsKey("test.malformed.rejecting", default = "d", serializer = RejectingSerializer)
  }

  /** Follows the contract: it rejects every stored value with [IllegalArgumentException]. */
  private object RejectingSerializer : Serializer<String> {
    override fun serialize(value: String): SettingsValue = StringValue(value)

    override fun deserialize(value: SettingsValue): String = throw IllegalArgumentException("the test rejects the value")
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  private val app = SettingsContext.app()

  @BeforeEach
  fun seedMalformedValues(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    provider.seed(app, MapValue(mapOf(
      marker.identity to StringValue("landed"),
      scalar.identity to StringValue("not a number"),
      listElement.identity to ListValue(listOf(StringValue("good"), IntValue(1))),
      mapValue.identity to MapValue(mapOf("k" to IntValue(1))),
      nullableScalar.identity to StringValue("not a number"),
      rejecting.identity to StringValue("any"),
    )))
    awaitEquals("landed") { marker[app] }
  }

  @Test
  fun `a built-in serializer rejects a wrong scalar, list element, map value and nullable value with a warning`() {
    val (values, warnings) = collectingWarnings {
      listOf(scalar[app], listElement[app], mapValue[app], nullableScalar[app])
    }

    assertEquals(listOf(-1, listOf("d"), mapOf("k" to "d"), -1), values)
    for (key in listOf(scalar, listElement, mapValue, nullableScalar)) {
      assertTrue(warnings.any { key.identity in it }, "a warning names '${key.identity}': $warnings")
    }
  }

  @Test
  fun `a custom serializer that throws IllegalArgumentException falls back with a warning`() {
    val (value, warnings) = collectingWarnings { rejecting[app] }

    assertEquals("d", value)
    assertTrue(warnings.any { rejecting.identity in it }, "a warning names the key: $warnings")
  }
}
