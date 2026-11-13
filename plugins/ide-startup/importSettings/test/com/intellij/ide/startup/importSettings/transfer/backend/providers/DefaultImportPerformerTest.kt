// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers

import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultImportPerformerTest {
  @Test
  fun `performEdt runs only the partial performers that will perform`() {
    val performing = TestPartialImportPerformer(willPerform = true)
    val skipped = TestPartialImportPerformer(willPerform = false)
    val settings = Settings()

    DefaultImportPerformer.withCustomPartialPerformers(listOf(performing, skipped)).performEdt(null, settings)

    assertEquals(listOf(settings), performing.performEdtCalls)
    assertTrue(skipped.performEdtCalls.isEmpty())
  }

  @Test
  fun `perform runs only the partial performers that will perform`() {
    val performing = TestPartialImportPerformer(willPerform = true)
    val skipped = TestPartialImportPerformer(willPerform = false)
    val settings = Settings()

    DefaultImportPerformer.withCustomPartialPerformers(listOf(performing, skipped)).perform(null, settings, EmptyProgressIndicator(ModalityState.nonModal()))

    assertEquals(listOf(settings), performing.performCalls)
    assertTrue(skipped.performCalls.isEmpty())
  }

  @Test
  fun `collectAllRequiredPlugins includes only plugins of the partial performers that will perform`() {
    val requiredPluginId = PluginId.getId("test.required.plugin")
    val partials = listOf(
      TestPartialImportPerformer(willPerform = true, requiredPlugins = setOf(requiredPluginId)),
      TestPartialImportPerformer(willPerform = false, requiredPlugins = setOf(PluginId.getId("test.skipped.plugin"))),
    )

    val pluginIds = DefaultImportPerformer.withCustomPartialPerformers(partials).collectAllRequiredPlugins(Settings())

    assertEquals(setOf(requiredPluginId), pluginIds)
  }
}
