// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.updateSettings.impl.PluginUpdateSource
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.labels.LinkListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginDetailsPageComponentTest {
  @Test
  fun `split legacy details keep installation target options`() {
    assertThat(requiresInstallOptionButton(useSecondaryButtons = false, combinedPluginManagerEnabled = true)).isTrue()
    assertThat(requiresInstallOptionButton(useSecondaryButtons = false, combinedPluginManagerEnabled = false)).isFalse()
    assertThat(requiresInstallOptionButton(useSecondaryButtons = true, combinedPluginManagerEnabled = false)).isTrue()
  }

  @Test
  fun `the detached details panel ignores an update source result`(): Unit = timeoutRunBlocking {
    LafManager.getInstance()
    val pluginId = PluginId.getId("com.intellij")
    val plugin = PluginUiModelAdapter(checkNotNull(PluginManagerCore.getPlugin(pluginId)))
    assertThat(UiPluginManager.getInstance().getPluginInstallationState(pluginId).fullyInstalled).isTrue()

    val sourceRequested = CompletableDeferred<Unit>()
    val resumeSource = CompletableDeferred<Unit>()
    val details = withContext(Dispatchers.EDT) {
      val model = MyPluginModel(null).apply {
        coroutineScope = this@timeoutRunBlocking
        setTopController(Configurable.TopComponentController.EMPTY)
      }
      val facade = object : PluginModelFacade(model) {
        override suspend fun getPendingPluginUpdateSource(pluginId: PluginId): PluginUpdateSource? {
          sourceRequested.complete(Unit)
          resumeSource.await()
          return null
        }
      }
      PluginDetailsPageComponent(facade, LinkListener { _, _ -> }, isMarketplace = false).also(model::addDetailPanel)
    }
    try {
      val render = launch(Dispatchers.EDT) {
        details.showPluginImpl(plugin, null)
      }
      sourceRequested.await()
      withContext(Dispatchers.EDT) { details.detach() }
      resumeSource.complete(Unit)
      render.join()
    }
    finally {
      resumeSource.complete(Unit)
      withContext(NonCancellable + Dispatchers.EDT) {
        details.detach()
      }
    }
  }
}
