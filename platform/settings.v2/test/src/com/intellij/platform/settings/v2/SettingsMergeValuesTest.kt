// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins [SettingsKey.values]: it gives the value of each layer along the application, project and file chain, widest
 * first, so a caller can merge them instead of taking the narrowest hit. For a map-valued key, a fold keeps the
 * entries of the wider maps under an empty narrower map. For a slotted child key, each layer gives the value of its
 * selected slot, and a context with no layer that holds the slot gives nothing.
 */
@TestApplication
class SettingsMergeValuesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
    private val sourceRootFixture = moduleFixture.sourceRootFixture()

    private val limits = SettingsKey("test.merge.limits", default = emptyMap(), serializer = IntSerializer.mapOf())

    private val currentProfile = SettingsKey("test.merge.profile", default = "default")
    private val profileData = SettingsMapKey("test.merge.profileData", discriminatorKey = currentProfile)
    private val inspection = SettingsKey("test.merge.inspection", default = "inspectionDefault", storageKey = profileData)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectFixture.get()
  private val dir get() = sourceRootFixture.get().virtualFile

  @Test
  fun `values exposes the whole chain so populated wider maps survive an empty narrower one`(): Unit = runBlocking {
    val appCtx = SettingsContext.app()
    val projectCtx = SettingsContext.project(project)
    val fileCtx = readAction { SettingsContext.file(dir, project) }

    limits[appCtx] = mapOf("a" to 1, "b" to 2)
    limits[projectCtx] = mapOf("b" to 20, "c" to 3)
    limits[fileCtx] = emptyMap()

    awaitEquals(mapOf("a" to 1, "b" to 2)) { limits[appCtx] }
    awaitEquals(mapOf("b" to 20, "c" to 3)) { limits[projectCtx] }
    // The file value is empty, which equals the key default, so wait on the chain being fully populated instead.
    awaitEquals(3) { limits.values(fileCtx).size }

    assertEquals(emptyMap<String, Int>(), limits[fileCtx], "empty narrowest layer shadows the wider ones")

    assertEquals(listOf(mapOf("a" to 1, "b" to 2), mapOf("b" to 20, "c" to 3), emptyMap()), limits.values(fileCtx))
    // A narrower layer wins on a collision: the project "b" beats the application "b".
    val merged = limits.values(fileCtx).fold(mutableMapOf<String, Int>()) { acc, m -> acc.putAll(m); acc }
    assertEquals(mapOf("a" to 1, "b" to 20, "c" to 3), merged, "folding values unions the whole chain")
  }

  @Test
  fun `values on a slotted child key spans the chain and skips a layer missing the slot`(): Unit = runBlocking {
    val provider = TestLayerProvider()
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    val appCtx = SettingsContext.app()
    val fileCtx = readAction { SettingsContext.file(dir, project) }

    inspection[appCtx.accessFor(inspection)] = "appValue"
    inspection[fileCtx.accessFor(inspection)] = "fileValue"
    // The project context between them gets no slot data.

    awaitEquals("fileValue") { inspection[fileCtx] }
    awaitEquals("appValue") { inspection[appCtx] }

    assertEquals(listOf("appValue", "fileValue"), inspection.values(fileCtx))
  }
}
