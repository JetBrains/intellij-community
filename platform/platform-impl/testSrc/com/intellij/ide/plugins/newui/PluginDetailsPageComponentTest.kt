// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.labels.LinkListener
import kotlinx.coroutines.CoroutineStart
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
    val host = withContext(Dispatchers.EDT) {
      LegacyPluginUiHost(parentScope = this@timeoutRunBlocking, operationScope = this@timeoutRunBlocking)
    }
    try {
      val render = withContext(Dispatchers.EDT) {
        val details = host.createDetails(LinkListener { _, _ -> }, marketplace = false)
        val plugin = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId("detached.details.plugin"))
          .setName("Detached Details Plugin")
          .build()
        val modality = ModalityState.stateForComponent(details).asContextElement()
        withContext(Dispatchers.EDT + modality) {
          val job = this@timeoutRunBlocking.launch(Dispatchers.EDT + modality, start = CoroutineStart.UNDISPATCHED) {
            details.showPluginImpl(plugin, null)
          }
          assertThat(job.isCompleted).isFalse()
          details.detach()
          job
        }
      }
      render.join()
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        host.dispose(closeSession = false)
      }
    }
  }
}
