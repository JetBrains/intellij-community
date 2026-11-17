// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.platform.settings.v2.SettingsValue.IntValue
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

/** Holds the entries of an enum key. [WITH_BODY] has a body, so its class is a subclass of the enum. */
private enum class TestMode {
  FIRST,
  SECOND,
  WITH_BODY {
    override fun label(): String = "with a body"
  };

  open fun label(): String = name
}

/** Holds constants whose names differ only by the case or by an underscore. */
@Suppress("EnumEntryName")
private enum class LookAlikeName { AB, A_B, ab }

/**
 * Checks the enum serializer: a key infers it from an enum default, and it stores an entry by its constant name
 * (ADR 0178). A test layer holds the values, so a test can read the stored form and seed a hand-written one.
 */
@TestApplication
class SettingsEnumSerializerTest {
  companion object {
    private val marker = SettingsKey("test.enum.marker", default = "")
    private val mode = SettingsKey("test.enum.mode", default = TestMode.FIRST)
    private val modeWithBody = SettingsKey("test.enum.modeWithBody", default = TestMode.WITH_BODY)
    private val nullableMode = SettingsKey<TestMode?>("test.enum.nullableMode", default = TestMode.FIRST,
                                                      serializer = enumSerializer<TestMode>().nullable())
    private val modes = SettingsKey("test.enum.modes", default = emptyList(), serializer = enumSerializer<TestMode>().listOf())
    private val lookAlike = SettingsKey("test.enum.lookAlike", default = LookAlikeName.AB)
    private val modeByClass = SettingsKey<TestMode?>("test.enum.modeByClass", default = null,
                                                     serializer = enumSerializer(TestMode::class.java).nullable())
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
  fun `a key with an enum default needs no serializer and stores the constant name`(): Unit = runBlocking {
    mode[app] = TestMode.SECOND

    assertEquals(TestMode.SECOND, mode[app])
    assertEquals(StringValue("SECOND"), storedValue(mode))
  }

  @Test
  fun `an entry with a body is inferred and stored by its constant name`(): Unit = runBlocking {
    assertEquals(TestMode.WITH_BODY, modeWithBody[app], "the default of a key whose default has a body")

    mode[app] = TestMode.WITH_BODY

    assertEquals(TestMode.WITH_BODY, mode[app])
    assertEquals(StringValue("WITH_BODY"), storedValue(mode))
  }

  @Test
  fun `an unknown name and a value that is not a string fall back to the default with a warning`(): Unit = runBlocking {
    provider.seed(app, MapValue(mapOf(
      marker.identity to StringValue("landed"),
      mode.identity to StringValue("second"),
      modeWithBody.identity to IntValue(1),
    )))
    awaitEquals("landed") { marker[app] }

    val (values, warnings) = collectingWarnings { listOf(mode[app], modeWithBody[app]) }

    assertEquals(listOf(TestMode.FIRST, TestMode.WITH_BODY), values)
    for (key in listOf(mode, modeWithBody)) {
      assertTrue(warnings.any { key.identity in it }, "a warning names '${key.identity}': $warnings")
    }
  }

  @Test
  fun `the enum serializer composes with nullable and listOf`(): Unit = runBlocking {
    nullableMode[app] = null
    modes[app] = listOf(TestMode.SECOND, TestMode.WITH_BODY)

    assertEquals(null, nullableMode[app])
    assertEquals(listOf(TestMode.SECOND, TestMode.WITH_BODY), modes[app])

    nullableMode[app] = TestMode.SECOND
    assertEquals(TestMode.SECOND, nullableMode[app])
  }

  @Test
  fun `the class form of the enum serializer stores the constant name`(): Unit = runBlocking {
    modeByClass[app] = TestMode.SECOND

    assertEquals(TestMode.SECOND, modeByClass[app])
    assertEquals(StringValue("SECOND"), storedValue(modeByClass))
  }

  @Test
  fun `constants that differ only by the case or an underscore stay distinct`(): Unit = runBlocking {
    for (entry in LookAlikeName.entries) {
      lookAlike[app] = entry

      assertEquals(entry, lookAlike[app])
      assertEquals(StringValue(entry.name), storedValue(lookAlike))
    }
  }

  /** Writes the pending saves and returns the value that the local layer of the application stores for [key]. */
  private suspend fun storedValue(key: SettingsKey<*>): SettingsValue? {
    SettingsVault.getInstance().flush(app)
    return provider.saved(app, LayerLocality.LOCAL)?.get(key)
  }
}
