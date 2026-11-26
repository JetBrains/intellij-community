// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.BooleanValue
import com.intellij.platform.settings.v2.SettingsValue.DoubleValue
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.LongValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Checks [FloatSerializer]: a key infers it, and it stores the shortest decimal form of a float (ADR 00F5FN). Checks
 * also that a float key and a double key read a stored integer. A test layer holds the values, so a test can read
 * the stored form and seed a hand-written one.
 */
@TestApplication
class SettingsFloatSerializerTest {
  companion object {
    private val marker = SettingsKey("test.float.marker", default = "")
    private val scale = SettingsKey("test.float.scale", default = 1f)
    private val scaleOfStaticType = SettingsKey<Float?>("test.float.scaleOfStaticType")
    private val scaleOfNonNullStaticType = SettingsKey("test.float.scaleOfNonNullStaticType", default = 1f,
                                                       serializer = inferSerializer<Float>())
    private val nullableScale = SettingsKey<Float?>("test.float.nullableScale", default = 1f, serializer = FloatSerializer.nullable())
    private val scales = SettingsKey("test.float.scales", default = emptyList(), serializer = FloatSerializer.listOf())
    private val scalesByName = SettingsKey("test.float.scalesByName", default = emptyMap(), serializer = FloatSerializer.mapOf())
    private val ratio = SettingsKey("test.float.ratio", default = 0.5)
    private val scaleFromInt = SettingsKey("test.float.scaleFromInt", default = 1f)
    private val scaleFromLong = SettingsKey("test.float.scaleFromLong", default = 1f)
    private val ratioFromInt = SettingsKey("test.float.ratioFromInt", default = 0.5)
    private val ratioFromLong = SettingsKey("test.float.ratioFromLong", default = 0.5)
    private val scaleFromPreciseDouble = SettingsKey("test.float.scaleFromPreciseDouble", default = 1f)
    private val scaleOutOfRange = SettingsKey("test.float.scaleOutOfRange", default = 1f)
    private val scaleOutOfNegativeRange = SettingsKey("test.float.scaleOutOfNegativeRange", default = 1f)
    private val scaleFromInfinity = SettingsKey("test.float.scaleFromInfinity", default = 1f)
    private val scaleFromNegativeInfinity = SettingsKey("test.float.scaleFromNegativeInfinity", default = 1f)
    private val scaleFromNaN = SettingsKey("test.float.scaleFromNaN", default = 1f)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  private val app = SettingsContext.app()

  @BeforeEach
  fun installTestLayer() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
  }

  @Test
  fun `a key with a float default needs no serializer and stores the shortest decimal form`(): Unit = runBlocking {
    assertEquals(1f, scale[app], "the default of the key")

    scale[app] = 1.1f

    assertEquals(1.1f, scale[app])
    assertEquals(DoubleValue(1.1), storedValue(scale))
  }

  @Test
  fun `a float reads back exactly, also when its shortest decimal form does not`(): Unit = runBlocking {
    // The last two: the double of the shortest decimal form narrows to a neighbor float, because it rounds twice.
    val values = listOf(1e10f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, 7.038531E-26f, -7.038531E-26f)
    for (value in values) {
      scale[app] = value

      assertEquals(value, scale[app], "the read of $value")
      assertEquals(value, (storedValue(scale) as DoubleValue).value.toFloat(), "the stored form of $value")
    }
  }

  @Test
  fun `a float key reads a stored double with more digits than a float holds as the nearest float`(): Unit = runBlocking {
    provider.seed(app, MapValue(mapOf(
      marker.identity to StringValue("landed"),
      scaleFromPreciseDouble.identity to DoubleValue(1.100000023841858),
    )))
    awaitEquals("landed") { marker[app] }

    assertEquals(1.1f, scaleFromPreciseDouble[app])
  }

  @Test
  fun `a finite double out of the float range makes a float key fall back to the default with a warning`(): Unit = runBlocking {
    provider.seed(app, MapValue(mapOf(
      marker.identity to StringValue("landed"),
      scaleOutOfRange.identity to DoubleValue(1e40),
      scaleOutOfNegativeRange.identity to DoubleValue(-1e40),
    )))
    awaitEquals("landed") { marker[app] }

    val (values, warnings) = collectingWarnings { listOf(scaleOutOfRange[app], scaleOutOfNegativeRange[app]) }

    assertEquals(listOf(1f, 1f), values)
    for (key in listOf(scaleOutOfRange, scaleOutOfNegativeRange)) {
      assertTrue(warnings.any { key.identity in it }, "a warning names '${key.identity}': $warnings")
    }
  }

  @Test
  fun `a hand-written infinity or NaN of a config file reads as the same float`(): Unit = runBlocking {
    provider.seed(app, JsonSettingsCodec.decode("""
      {
        "${marker.identity}": "landed",
        "${scaleFromInfinity.identity}": Infinity,
        "${scaleFromNegativeInfinity.identity}": -Infinity,
        "${scaleFromNaN.identity}": NaN
      }
    """.trimIndent()))
    awaitEquals("landed") { marker[app] }

    assertEquals(Float.POSITIVE_INFINITY, scaleFromInfinity[app])
    assertEquals(Float.NEGATIVE_INFINITY, scaleFromNegativeInfinity[app])
    assertTrue(scaleFromNaN[app].isNaN(), "the read of NaN: ${scaleFromNaN[app]}")
  }

  @Test
  fun `a key infers the float serializer from its static type`(): Unit = runBlocking {
    assertNull(scaleOfStaticType[app], "the default of a key with no default")

    scaleOfStaticType[app] = 1.1f
    scaleOfNonNullStaticType[app] = 2.2f

    assertEquals(1.1f, scaleOfStaticType[app])
    assertEquals(DoubleValue(1.1), storedValue(scaleOfStaticType))
    assertEquals(2.2f, scaleOfNonNullStaticType[app])
    assertEquals(DoubleValue(2.2), storedValue(scaleOfNonNullStaticType))

    scaleOfStaticType[app] = null
    assertNull(scaleOfStaticType[app])
  }

  @Test
  fun `the float serializer composes with nullable, listOf and mapOf`(): Unit = runBlocking {
    nullableScale[app] = null
    scales[app] = listOf(1.1f, 2f)
    scalesByName[app] = mapOf("a" to 1.1f, "b" to 2f)

    assertNull(nullableScale[app])
    assertEquals(listOf(1.1f, 2f), scales[app])
    assertEquals(mapOf("a" to 1.1f, "b" to 2f), scalesByName[app])
    assertEquals(ListValue(listOf(DoubleValue(1.1), DoubleValue(2.0))), storedValue(scales))
    assertEquals(MapValue(mapOf("a" to DoubleValue(1.1), "b" to DoubleValue(2.0))), storedValue(scalesByName))

    nullableScale[app] = 1.1f
    assertEquals(1.1f, nullableScale[app])
  }

  @Test
  fun `a float key and a double key read a stored int and a stored long`(): Unit = runBlocking {
    provider.seed(app, MapValue(mapOf(
      marker.identity to StringValue("landed"),
      scaleFromInt.identity to IntValue(2),
      scaleFromLong.identity to LongValue(3L),
      ratioFromInt.identity to IntValue(2),
      ratioFromLong.identity to LongValue(3L),
    )))
    awaitEquals("landed") { marker[app] }

    assertEquals(2f, scaleFromInt[app])
    assertEquals(3f, scaleFromLong[app])
    assertEquals(2.0, ratioFromInt[app])
    assertEquals(3.0, ratioFromLong[app])
  }

  @Test
  fun `a string and a boolean make a float key and a double key fall back to the default with a warning`(): Unit = runBlocking {
    provider.seed(app, MapValue(mapOf(
      marker.identity to StringValue("landed"),
      scale.identity to StringValue("1.5"),
      scaleFromInt.identity to BooleanValue(true),
      ratio.identity to StringValue("1.5"),
      ratioFromInt.identity to BooleanValue(true),
    )))
    awaitEquals("landed") { marker[app] }

    val (values, warnings) = collectingWarnings { listOf(scale[app], scaleFromInt[app], ratio[app], ratioFromInt[app]) }

    assertEquals(listOf(1f, 1f, 0.5, 0.5), values)
    for (key in listOf(scale, scaleFromInt, ratio, ratioFromInt)) {
      assertTrue(warnings.any { key.identity in it }, "a warning names '${key.identity}': $warnings")
    }
  }

  /** Writes the pending saves and returns the value that the local layer of the application stores for [key]. */
  private suspend fun storedValue(key: SettingsKey<*>): SettingsValue? {
    SettingsVault.getInstance().flush(app)
    return provider.saved(app, LayerLocality.LOCAL)?.get(key)
  }
}
