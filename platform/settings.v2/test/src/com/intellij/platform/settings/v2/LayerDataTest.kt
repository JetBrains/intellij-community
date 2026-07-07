// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.platform.settings.v2.SettingsValue.BooleanValue
import com.intellij.platform.settings.v2.SettingsValue.IntValue
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Checks that [LayerData] builds from raw serialized entries and from typed keys, and that it reads the values back by
 * key. Also checks that a list or a map value copies the collection that it gets. A later change of that collection
 * then does not reach the value.
 */
class LayerDataTest {
  private val context = object : SettingsContext {
    override val parentContext: SettingsContext? = null
    override val kind: SettingsContextKind = SettingsContextKind.APPLICATION
    override val identity: String = "test"
  }

  private val provider = object : LayerProvider {}

  @Test
  fun `builds from raw serialized entries and reads them back by a key of matching identity`() {
    val key = SettingsKey("layerdata.raw", default = "")
    val data = LayerData.Builder(context, provider, LayerLocality.TEAM).apply {
      this[key.identity] = StringValue("from text")
    }.build()

    val raw = data.map[key]
    assertEquals(StringValue("from text"), raw, "stored as the serialized value")
    assertEquals("from text", key.serializer.deserialize(raw!!), "and deserializes back to the typed value")
  }

  @Test
  fun `builds from typed keys of different value types and reads the serialized values`() {
    val stringKey = SettingsKey("layerdata.string", default = "")
    val intKey = SettingsKey("layerdata.int", default = 0)
    val boolKey = SettingsKey("layerdata.bool", default = false)

    val data = LayerData.Builder(context, provider, LayerLocality.TEAM).apply {
      this[stringKey] = "hello"
      this[intKey] = 42
      this[boolKey] = true
    }.build()

    assertEquals(StringValue("hello"), data.map[stringKey])
    assertEquals(IntValue(42), data.map[intKey])
    assertEquals(BooleanValue(true), data.map[boolKey])

    assertEquals("hello", stringKey.serializer.deserialize(data.map[stringKey]!!))
    assertEquals(42, intKey.serializer.deserialize(data.map[intKey]!!))
    assertEquals(true, boolKey.serializer.deserialize(data.map[boolKey]!!))
  }

  @Test
  fun `returns null for a key the layer does not hold`() {
    val present = SettingsKey("layerdata.present", default = "")
    val absent = SettingsKey("layerdata.absent", default = "")
    val data = LayerData.Builder(context, provider, LayerLocality.TEAM).apply { this[present] = "x" }.build()

    assertNull(data.map[absent])
  }

  @Test
  fun `a list value does not change when the list it was built from changes`() {
    val items = mutableListOf<SettingsValue>(StringValue("a"))
    val value = ListValue(items)

    items += StringValue("b")

    assertEquals(listOf(StringValue("a")), value.items)
  }

  @Test
  fun `a map value does not change when the map it was built from changes`() {
    val values = linkedMapOf<String, SettingsValue>("a" to StringValue("1"))
    val value = MapValue(values)

    values["b"] = StringValue("2")

    assertEquals(mapOf("a" to StringValue("1")), value.values)
  }

  @Test
  fun `a map value builder used again after build does not change the built value`() {
    val builder = MapValue.Builder()
    builder["a"] = StringValue("1")
    val first = builder.build()

    builder["b"] = StringValue("2")

    assertEquals(mapOf("a" to StringValue("1")), first.values)
    assertEquals(mapOf("a" to StringValue("1"), "b" to StringValue("2")), builder.build().values)
  }
}
