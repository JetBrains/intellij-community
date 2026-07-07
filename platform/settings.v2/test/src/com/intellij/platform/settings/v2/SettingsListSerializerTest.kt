// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.ListValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Checks the keys over lists that the [Serializer.listOf] combinator declares. An element can be non-null, or nullable
 * through [nullable], and the list itself can be nullable. Each key falls back to its default when it has no value, and
 * makes a round trip of a written value. The nullable list also makes a round trip of a stored `null`.
 *
 * A stored list also stays the same when the serializer changes the list that it built the value from.
 */
@TestApplication
class SettingsListSerializerTest {
  companion object {
    private val strings = SettingsKey("test.list.strings", default = emptyList(), serializer = StringSerializer.listOf())
    private val nullableElements = SettingsKey("test.list.nullableElements", default = emptyList(), serializer = StringSerializer.nullable().listOf())
    private val nullableList = SettingsKey("test.list.nullableList", default = null, serializer = StringSerializer.listOf().nullable())
    private val keptItems = SettingsKey("test.list.keptItems", default = emptyList(), serializer = ItemsKeepingSerializer)
  }

  /** Builds each [ListValue] from a list that it keeps in [itemLists], so a test can change that list later. */
  private object ItemsKeepingSerializer : Serializer<List<String>> {
    val itemLists: MutableList<MutableList<SettingsValue>> = ArrayList()

    override fun serialize(value: List<String>): SettingsValue {
      val items = value.mapTo(ArrayList<SettingsValue>(), ::StringValue)
      itemLists += items
      return ListValue(items)
    }

    override fun deserialize(value: SettingsValue): List<String> = (value as ListValue).items.map { (it as StringValue).value }
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider()

  @Test
  fun `a list of non-null elements defaults empty and round-trips`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    assertEquals(emptyList<String>(), strings[context])

    strings[context] = listOf("a.kt", "b.kt")
    assertEquals(listOf("a.kt", "b.kt"), strings[context])
  }

  @Test
  fun `a list of nullable elements round-trips nulls among values`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    nullableElements[context] = listOf("a", null, "b")
    assertEquals(listOf("a", null, "b"), nullableElements[context])
  }

  @Test
  fun `a nullable list defaults null and round-trips both a value and an explicit null`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    assertEquals(null, nullableList[context])

    nullableList[context] = listOf("x")
    assertEquals(listOf("x"), nullableList[context])

    nullableList[context] = null
    assertEquals(null, nullableList[context])
  }

  @Test
  fun `a stored list does not change when the serializer changes the list it built the value from`(): Unit = runBlocking {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val context = SettingsContext.app()

    keptItems[context] = listOf("a.kt")
    ItemsKeepingSerializer.itemLists.forEach { it += StringValue("intruder.kt") }

    assertEquals(listOf("a.kt"), keptItems[context])
  }
}
