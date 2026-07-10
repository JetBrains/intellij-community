// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandlerProvider
import com.intellij.openapi.updateSettings.impl.PluginUpdatesModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.ui.components.Badge
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.assertTrue

@TestApplication
internal class ConfigurableNewOptionsTest {
  @BeforeEach
  fun registerNoopPluginUpdateHandler(@TestDisposable disposable: Disposable) {
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(
      PluginUpdateHandlerProvider::class.java,
      NoopPluginUpdateHandlerProvider,
      disposable,
    )
  }

  @AfterEach
  fun drainPluginUpdates(): Unit = timeoutRunBlocking {
    PluginUpdatesService.getInstance().awaitUpdates()
  }

  @Test
  fun `leaf configurable NewOptions marker matches rendered new badges`() = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val leaves = mutableListOf<LeafConfigurable>()
    collectLeaves(ConfigurableExtensionPointUtil.getConfigurableGroup(null, true).configurables, emptyList(), leaves)

    val mismatches = buildList {
      for ((configurable, path) in leaves) {
        if (allowsMismatch(configurable)) {
          continue
        }

        val markerPresent = isNewOptions(configurable)
        val badgePresent = hasNewBadge(configurable, path)
        if (markerPresent != badgePresent) {
          add("${path.joinToString(" > ")}: ${configurable.javaClass.name} is ${markerStatus(markerPresent)} but ${badgeStatus(badgePresent)}")
        }
      }
    }

    assertTrue(mismatches.isEmpty(), mismatches.joinToString(separator = "\n", prefix = "NewOptions marker mismatches:\n"))
  }

  private fun collectLeaves(configurables: Array<out UnnamedConfigurable>, path: List<String>, result: MutableList<LeafConfigurable>) {
    for (configurable in configurables) {
      val childPath = path + when (configurable) {
        is Configurable -> displayName(configurable)
        else -> "<unnamed>"
      }
      val children = when (configurable) {
        is Configurable.Composite -> configurable.configurables
        is CompositeConfigurable<*> -> configurable.configurables.toTypedArray()
        else -> emptyArray()
      }

      if (children.isEmpty()) {
        result += LeafConfigurable(configurable, childPath)
      }
      else {
        collectLeaves(children, childPath, result)
      }
    }
  }

  private fun hasNewBadge(configurable: UnnamedConfigurable, path: List<String>): Boolean {
    val component = try {
      WriteIntentReadAction.compute { configurable.createComponent() }
    }
    catch (e: Throwable) {
      throw AssertionError("Failed to create configurable component for ${path.joinToString(" > ")} (${configurable.javaClass.name})", e)
    }

    return try {
      component != null && containsNewBadge(component)
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  private fun containsNewBadge(component: Component): Boolean = UIUtil.uiTraverser(component)
    .traverse()
    .filter(JLabel::class.java)
    .any { it.icon === Badge.new }

  private fun isNewOptions(configurable: UnnamedConfigurable): Boolean {
    return hasMarker(configurable, Configurable.NewOptions::class.java)
  }

  private fun allowsMismatch(configurable: UnnamedConfigurable): Boolean {
    return hasMarker(configurable, Configurable.NewOptionsMismatchAllowed::class.java)
  }

  private fun hasMarker(configurable: UnnamedConfigurable, markerClass: Class<*>): Boolean {
    if (markerClass.isInstance(configurable)) {
      return true
    }

    if (configurable is ConfigurableWrapper) {
      val rawConfigurable = configurable.rawConfigurable
      if (markerClass.isInstance(rawConfigurable)) {
        return true
      }

      val configurableType = configurable.extensionPoint.configurableType
      if (configurableType != null && markerClass.isAssignableFrom(configurableType)) {
        return true
      }
    }

    return false
  }

  private fun displayName(configurable: Configurable): String {
    return try {
      configurable.displayName
    }
    catch (_: Throwable) {
      configurable.javaClass.name
    }
  }

  private fun markerStatus(markerPresent: Boolean): String {
    return if (markerPresent) "marked with Configurable.NewOptions" else "not marked with Configurable.NewOptions"
  }

  private fun badgeStatus(badgePresent: Boolean): String {
    return if (badgePresent) "renders Badge.new" else "does not render Badge.new"
  }

  private data class LeafConfigurable(val configurable: UnnamedConfigurable, val path: List<String>)
}

private object NoopPluginUpdateHandlerProvider : PluginUpdateHandlerProvider {
  override fun getPluginUpdateHandler(): PluginUpdateHandler = NoopPluginUpdateHandler
}

private object NoopPluginUpdateHandler : PluginUpdateHandler {
  override suspend fun loadAndStorePluginUpdates(buildNumber: String?, indicator: ProgressIndicator?): PluginUpdatesModel =
    PluginUpdatesModel(
      pluginUpdates = emptyList(),
      updatesFromCustomRepositories = emptyList(),
      incompatiblePluginNames = emptyList(),
      internalErrors = emptyMap(),
    )

  override suspend fun installUpdates(
    updates: Collection<PluginUiModel>,
    component: JComponent?,
    finishCallback: Runnable?,
    customRestarter: Consumer<Boolean>?,
  ) {
  }

  override suspend fun ignorePluginUpdates() {
  }
}
