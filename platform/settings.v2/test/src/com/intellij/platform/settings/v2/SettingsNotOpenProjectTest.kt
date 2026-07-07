// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.SettingsValue.StringValue
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerProvider
import com.intellij.platform.settings.v2.impl.providers.ProjectLayerService
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A context of a project that is not open has no model in the vault. A closed project and the default project are
 * such projects. A mutation there is dropped with a warning, and a read there resolves through the application layers.
 * An identity of a project that no open project has stands for a closed project. The test provider offers localities
 * only at the application context, so a mutation that is not dropped would fall through to it.
 */
@TestApplication
class SettingsNotOpenProjectTest {
  companion object {
    private val key = SettingsKey("test.notOpenProject.key", default = "def")
    private val other = SettingsKey("test.notOpenProject.other", default = "def")
    private val current = SettingsKey("test.notOpenProject.current", default = "dev")
    private val slots = SettingsMapKey("test.notOpenProject.slots", discriminatorKey = current)
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  private val provider = TestLayerProvider(localitiesAt = ::applicationLocalitiesOnly)
  private val app = SettingsContext.app()
  private val closedProject get() = requireNotNull(ContextFactory.getInstance().deserialize("project|not-open"))
  private val fileOfClosedProject get() = requireNotNull(ContextFactory.getInstance().deserialize("file|not-open|file:///root|a.txt"))
  private val rootOfClosedProject get() = requireNotNull(ContextFactory.getInstance().deserialize("file|not-open|file:///root|"))
  private val defaultProject get() = SettingsContext.project(ProjectManager.getInstance().defaultProject)

  private fun applicationLocalitiesOnly(context: SettingsContext): Set<LayerLocality> =
    if (context.realContext.kind == SettingsContextKind.APPLICATION) LayerLocality.entries.toSet() else emptySet()

  private suspend fun maskProviders() {
    ExtensionTestUtil.maskExtensions(LayerProvider.EP_NAME, listOf(provider), disposable)
    app.awaitReady()
  }

  @Test
  fun `a write at a context of a closed project is dropped with a warning`(): Unit = runBlocking {
    maskProviders()
    for (context in listOf(closedProject, fileOfClosedProject, rootOfClosedProject)) {
      val (_, warnings) = collectingWarnings { key[context] = "written" }

      assertEquals("def", key[context], context.identity)
      assertEquals("def", key[app], context.identity)
      assertTrue(warnings.any { context.identity in it }) { "the drop is logged: $warnings" }
    }
  }

  @Test
  fun `a write at the default project context is dropped, and a read there gives the application value`(): Unit = runBlocking {
    maskProviders()
    key[app] = "app"

    val (_, warnings) = collectingWarnings { key[defaultProject] = "written" }

    assertEquals("app", key[defaultProject])
    assertEquals("app", key[app])
    assertTrue(warnings.any { defaultProject.identity in it }) { "the drop is logged: $warnings" }
  }

  @Test
  fun `a batch applies its entries at open contexts and drops the others`(): Unit = runBlocking {
    maskProviders()

    collectingWarnings { app.mutate().set(key, "applied").set(other, "dropped", context = closedProject).apply() }

    assertEquals("applied", key[app])
    assertEquals("def", other[closedProject])
    assertEquals("def", other[app])
  }

  @Test
  fun `a slot operation at a closed project context is dropped with a warning`(): Unit = runBlocking {
    maskProviders()

    val (_, warnings) = collectingWarnings { slots.renameSlot(closedProject, "dev", "prod") }

    assertTrue(warnings.any { closedProject.identity in it }) { "the drop is logged: $warnings" }
  }

  @Test
  fun `the project layer offers no locality at a context of a project that is not open`() {
    val projectLayer = ProjectLayerProvider()
    for (context in listOf(closedProject, defaultProject)) {
      assertEquals(emptySet<LayerLocality>(), projectLayer.availableLocalities(context), context.identity)
    }
  }

  @Test
  fun `a save of a project with no base path is dropped with a warning`(): Unit = runBlocking {
    val project = ProjectManager.getInstance().defaultProject
    assertNull(project.basePath)
    val context = SettingsContext.project(project)
    val slices = mapOf(context to MapValue(mapOf(key.identity to StringValue("written"))))

    val (result, warnings) = collectingWarnings {
      coroutineScope {
        val service = ProjectLayerService(project, this)
        Disposer.register(disposable, service)
        service.writeData(context, LayerLocality.LOCAL, slices)
      }
    }

    assertEquals(SaveResult.DROPPED, result)
    assertTrue(warnings.any { it.contains("\"written\"") }) { "the warning holds the dropped value: $warnings" }
  }
}
