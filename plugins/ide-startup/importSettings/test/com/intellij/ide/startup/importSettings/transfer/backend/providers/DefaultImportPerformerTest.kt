// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers

import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@TestApplication
class DefaultImportPerformerTest {
  @TestDisposable
  lateinit var disposable: Disposable

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

  @Test
  fun `installPlugins returns NoPlugins when the required plugins are installed and a custom repository has plugins`(@TempDir tempDir: Path) {
    val repository = tempDir.resolve("plugins.xml").apply { writeText(CUSTOM_REPOSITORY) }
    ExtensionTestUtil.maskExtensions(UpdateSettingsProvider.EP_NAME, listOf(TestUpdateSettingsProvider(repository)), disposable)
    val performer = DefaultImportPerformer.withCustomPartialPerformers(listOf(TestPartialImportPerformer(willPerform = true)))

    val state = timeoutRunBlocking {
      performer.installPlugins(null, setOf(CORE_PLUGIN_ID), EmptyProgressIndicator(ModalityState.nonModal()))
    }

    assertEquals(PluginInstallationState.NoPlugins, state)
  }

  private class TestUpdateSettingsProvider(private val xmlFile: Path) : UpdateSettingsProvider {
    override fun getPluginRepositories(): List<String> = listOf(xmlFile.toUri().toURL().toString())
  }
}

private val CORE_PLUGIN_ID: PluginId = PluginId.getId("com.intellij")

private const val CUSTOM_REPOSITORY = """
  <?xml version="1.0" encoding="UTF-8"?>
  <plugin-repository>
    <category name="Test">
      <idea-plugin downloads="1" size="1024" date="1119060380000" url="">
        <name>Custom Repository Plugin</name>
        <id>test.custom.repository.plugin</id>
        <description>...</description>
        <version>1.0</version>
        <vendor>JetBrains</vendor>
        <idea-version since-build="133.193"/>
        <download-url>plugin.zip</download-url>
      </idea-plugin>
    </category>
  </plugin-repository>
""".trimIndent()
