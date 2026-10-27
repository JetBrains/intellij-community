// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandlerProvider
import com.intellij.openapi.updateSettings.impl.PluginUpdateProgressSink
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceListener
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceServiceImpl
import com.intellij.openapi.updateSettings.impl.PluginUpdatesModel
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.function.Consumer
import javax.swing.JComponent

@TestApplication
internal class DefaultPluginUpdatesProviderTest {
  @Test
  fun `completed checks reach current and later subscribers`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withProvider(disposable) { provider ->
      publishPluginUpdates(null, updatesModel())
      val service = PluginUpdatesService(this)
      service.flow().filterNotNull().first()
      val received = Channel<PluginUpdatesEvent>(Channel.UNLIMITED)
      val subscription = withContext(Dispatchers.UI) {
        service.subscribe { received.trySend(it) }
      }
      assertThat(received.receive().all).isEmpty()

      val enabled = pluginDto("enabled")
      val disabled = pluginDto("disabled")
      val custom = pluginDto("custom")
      val remote = pluginDto("remote").apply { source = PluginSource.REMOTE }
      publishPluginUpdates(null, updatesModel(listOf(enabled, remote), listOf(disabled), listOf(custom)))

      val event = received.receive()
      assertThat(event.enabledUpdates).containsExactly(enabled, remote)
      assertThat(event.disabledUpdates).containsExactly(disabled)
      assertThat(event.pluginNods).containsExactly(custom)
      assertThat(listOf(enabled.source, disabled.source, custom.source)).containsOnly(PluginSource.LOCAL)
      assertThat(remote.source).isEqualTo(PluginSource.REMOTE)
      assertThat(provider.pluginUpdateEvents().first()).isEqualTo(event)

      val lateReceived = Channel<PluginUpdatesEvent>(Channel.UNLIMITED)
      val lateSubscription = withContext(Dispatchers.UI) {
        service.subscribe { lateReceived.trySend(it) }
      }
      assertThat(lateReceived.receive()).isEqualTo(event)
      withContext(Dispatchers.UI) {
        subscription.cancel()
        lateSubscription.cancel()
      }
    }
  }

  @Test
  fun `an empty completed check clears cached updates`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withProvider(disposable) { provider ->
      publishPluginUpdates(null, updatesModel(listOf(pluginDto("available"))))
      val service = PluginUpdatesService(this)
      service.flow().filterNotNull().first { it.all.isNotEmpty() }

      publishPluginUpdates(null, updatesModel())

      assertThat(service.flow().filterNotNull().first { it.all.isEmpty() }.pluginNods).isEmpty()
      assertThat(provider.pluginUpdateEvents().first()?.all).isEmpty()
      assertThat(service.awaitHasUpdate(PluginId.getId("available"))).isFalse()
    }
  }

  @Test
  fun `checks for another IDE build keep the current snapshot`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withProvider(disposable) { provider ->
      val current = pluginDto("current")
      publishPluginUpdates(null, updatesModel(listOf(current)))

      publishPluginUpdates("999.1", updatesModel(listOf(pluginDto("future"))))

      assertThat(provider.pluginUpdateEvents().first()?.all).containsExactly(current)
    }
  }

  @Test
  @RegistryKey(key = "platform.enable.plugin.update.source.feature", value = "true")
  fun `saved source changes refresh updates`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    var nextModel = updatesModel(listOf(pluginDto("available")))
    val handler = object : PluginUpdateHandler {
      override suspend fun loadAndStorePluginUpdates(buildNumber: String?, indicator: ProgressIndicator?): PluginUpdatesModel {
        assertThat(buildNumber).isNull()
        return nextModel
      }

      override suspend fun installUpdates(
        updates: Collection<PluginUiModel>,
        component: JComponent?,
        finishCallback: Runnable?,
        customRestarter: Consumer<Boolean>?,
        progressSink: PluginUpdateProgressSink,
      ): Unit = error("Unexpected installation")

      override suspend fun ignorePluginUpdates(): Unit = error("Unexpected ignored updates")
    }
    ExtensionTestUtil.maskExtensions(PluginUpdateHandlerProvider.EP_NAME, listOf(object : PluginUpdateHandlerProvider {
      override fun getPluginUpdateHandler(): PluginUpdateHandler = handler
    }), disposable)

    withProvider(disposable) { provider ->
      publishPluginUpdates(null, updatesModel())
      val sources = PluginUpdateSourceServiceImpl()
      val pluginId = PluginId.getId("available")
      val source = sources.createCustomRepositoryPluginUpdateSourceId("https://example.com/plugins.xml")
      var sourceChanges = 0
      application.messageBus.connect(disposable).subscribe(PluginUpdateSourceListener.TOPIC, PluginUpdateSourceListener { sourceChanges++ })

      sources.setPluginUpdateSourceId(pluginId, source)
      val updates = provider.pluginUpdateEvents().filterNotNull().first { it.all.isNotEmpty() }
      assertThat(updates.enabledUpdates.map { it.pluginId }).containsExactly(pluginId)
      assertThat(sourceChanges).isEqualTo(1)

      sources.setPluginUpdateSourceId(pluginId, source)
      assertThat(sourceChanges).isEqualTo(1)

      nextModel = updatesModel()
      sources.erasePluginUpdateSourceId(pluginId)
      provider.pluginUpdateEvents().filterNotNull().first { it.all.isEmpty() }
      assertThat(sourceChanges).isEqualTo(2)

      sources.erasePluginUpdateSourceId(pluginId)
      assertThat(sourceChanges).isEqualTo(2)
    }
  }

  private suspend fun withProvider(
    disposable: Disposable,
    action: suspend CoroutineScope.(DefaultPluginUpdatesProvider) -> Unit,
  ): Unit = coroutineScope {
    val scope = childScope("DefaultPluginUpdatesProviderTest", Dispatchers.Default)
    try {
      val provider = DefaultPluginUpdatesProvider(scope)
      ExtensionTestUtil.maskExtensions(PluginUpdatesProvider.EP_NAME, listOf(provider), disposable)
      action(scope, provider)
    }
    finally {
      scope.cancel()
    }
  }

  private fun pluginDto(id: String): PluginDto = PluginDto(id, PluginId.getId(id))

  private fun updatesModel(
    enabled: List<PluginDto> = emptyList(),
    disabled: List<PluginDto> = emptyList(),
    custom: List<PluginDto> = emptyList(),
  ): PluginUpdatesModel = PluginUpdatesModel(enabled, disabled, custom, emptyList(), emptyMap())

  @Test
  fun `disabling retains known updates for later session enablement`() {
    val disabled = PluginDto("Disabled Plugin", PluginId.getId("disabled.plugin"))
    val enabled = PluginDto("Enabled Plugin", PluginId.getId("enabled.plugin"))
    val previouslyDisabled = PluginDto("Previously Disabled Plugin", PluginId.getId("previously.disabled.plugin"))
    val custom = PluginDto("Custom Plugin", PluginId.getId("custom.plugin"))
    val updates = PluginUpdatesEvent(listOf(disabled, enabled), listOf(previouslyDisabled), listOf(custom))

    val result = updates.withDisabledPlugins(setOf(disabled.pluginId))

    assertThat(result.enabledUpdates).containsExactly(enabled)
    assertThat(result.disabledUpdates).containsExactly(previouslyDisabled, disabled)
    assertThat(result.all).containsExactlyInAnyOrder(disabled, enabled, previouslyDisabled)
    assertThat(result.pluginNods).containsExactly(custom)
    assertThat(result.withDisabledPlugins(setOf(disabled.pluginId))).isSameAs(result)
  }

  @TestFactory
  fun `disabling merges sources for cached updates with the same plugin id`(): List<DynamicTest> {
    val sources = listOf(
      Triple(PluginSource.LOCAL, PluginSource.REMOTE, PluginSource.BOTH),
      Triple(PluginSource.REMOTE, PluginSource.LOCAL, PluginSource.BOTH),
      Triple(PluginSource.LOCAL, PluginSource.BOTH, PluginSource.BOTH),
      Triple(PluginSource.BOTH, PluginSource.LOCAL, PluginSource.BOTH),
      Triple(PluginSource.REMOTE, PluginSource.BOTH, PluginSource.BOTH),
      Triple(PluginSource.BOTH, PluginSource.REMOTE, PluginSource.BOTH),
      Triple(PluginSource.BOTH, PluginSource.BOTH, PluginSource.BOTH),
      Triple(PluginSource.LOCAL, PluginSource.LOCAL, PluginSource.LOCAL),
      Triple(PluginSource.REMOTE, PluginSource.REMOTE, PluginSource.REMOTE),
    )
    return sources.map { (disabledSource, enabledSource, expectedSource) ->
      dynamicTest("$disabledSource + $enabledSource") {
        val pluginId = PluginId.getId("split.plugin")
        val disabled = PluginDto("Split Plugin", pluginId).apply { source = disabledSource }
        val enabled = PluginDto("Split Plugin", pluginId).apply { source = enabledSource }
        val updates = PluginUpdatesEvent(listOf(enabled), listOf(disabled), emptyList())

        val result = updates.withDisabledPlugins(setOf(pluginId))

        assertThat(result.enabledUpdates).isEmpty()
        assertThat(result.disabledUpdates).hasSize(1)
        assertThat(result.disabledUpdates.single().pluginId).isEqualTo(pluginId)
        assertThat(result.disabledUpdates.single().source).isEqualTo(expectedSource)
        assertThat(result.withDisabledPlugins(setOf(pluginId))).isSameAs(result)
      }
    }
  }
}
