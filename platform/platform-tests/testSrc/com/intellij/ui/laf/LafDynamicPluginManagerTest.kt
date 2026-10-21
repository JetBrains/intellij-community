// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.configurationStore.BundledSchemeEP
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.BundledColorSchemeEPName
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerListener
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
internal class LafDynamicPluginManagerTest {
  @Test
  @Timeout(30)
  fun `read actions cannot see incomplete schemes during plugin loading`(
    @TempDir configPath: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    configPath.resolve("colors/Custom.icls").write("""
      <scheme name="Custom" version="142" parent_scheme="Default">
        <colors>
          <option name="CARET_COLOR" value="123456" />
        </colors>
      </scheme>
    """.trimIndent())
    val manager = createColorsManager(configPath, disposable)
    val customScheme = manager.getScheme("Custom")!!
    manager.schemeManager.setCurrent(customScheme, notify = false)
    val defaultScheme = DefaultColorSchemesManager.getInstance().firstScheme
    val editableCopyName = defaultScheme.editableCopyName
    assertThat(manager.getScheme(editableCopyName)).isNotNull()

    var readAllowedDuringReload: Boolean? = null
    var editableCopyRemoved = false
    manager.addColorScheme(object : EditorColorsSchemeImpl(defaultScheme) {
      init {
        name = "Reload observer"
      }

      override fun getSchemeState(): SchemeState {
        if (readAllowedDuringReload == null) {
          editableCopyRemoved = manager.getScheme(editableCopyName) == null
          readAllowedDuringReload = AppExecutorUtil.getAppExecutorService().submit<Boolean> {
            (application as ApplicationEx).tryRunReadAction {
              manager.getScheme(editableCopyName)
            }
          }.get(10, TimeUnit.SECONDS)
        }
        return SchemeState.NON_PERSISTENT
      }
    })

    val extensions = Disposer.newDisposable(disposable, "plugin extensions")
    try {
      loadPlugins { registerBundledColorScheme(extensions) }

      assertThat(editableCopyRemoved).isTrue()
      assertThat(readAllowedDuringReload).isFalse()
      assertThat(manager.getScheme(editableCopyName)).isNotNull()
      assertThat(manager.schemeManager.activeScheme).isSameAs(manager.getScheme("Custom")).isNotSameAs(customScheme)
      assertThat(manager.globalScheme.name).isEqualTo("Custom")
      assertThat(manager.globalScheme.getColor(EditorColors.CARET_COLOR)).isEqualTo(Color(0x123456))
    }
    finally {
      unloadPlugins { Disposer.dispose(extensions) }
    }
  }

  @Test
  fun `plugin events without a theme or a color scheme do not reload color schemes`(
    @TempDir configPath: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    createColorsManager(configPath, disposable)
    val reloadCount = countSchemeReloads(disposable)

    loadPlugins {}
    unloadPlugins {}

    assertThat(reloadCount.get()).isZero()
  }

  @Test
  fun `theme extension reloads color schemes`(
    @TempDir configPath: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    createColorsManager(configPath, disposable)
    val reloadCount = countSchemeReloads(disposable)

    val extensions = Disposer.newDisposable(disposable, "plugin extensions")
    try {
      loadPlugins { registerTheme(extensions) }
      assertThat(reloadCount.get()).isEqualTo(1)
    }
    finally {
      unloadPlugins { Disposer.dispose(extensions) }
    }
  }

  @Test
  fun `extensions of one plugin batch reload color schemes once`(
    @TempDir configPath: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    createColorsManager(configPath, disposable)
    val reloadCount = countSchemeReloads(disposable)

    val extensions = Disposer.newDisposable(disposable, "plugin extensions")
    try {
      loadPlugins {
        registerTheme(extensions)
        registerBundledColorScheme(extensions)
      }
      assertThat(reloadCount.get()).isEqualTo(1)
    }
    finally {
      unloadPlugins {
        Disposer.dispose(extensions)
        assertThat(reloadCount.get()).isEqualTo(1)
      }
    }
    assertThat(reloadCount.get()).isEqualTo(2)
  }
}

private fun createColorsManager(configPath: Path, disposable: Disposable): EditorColorsManagerImpl {
  val manager = EditorColorsManagerImpl(SchemeManagerFactoryBase.TestSchemeManagerFactory(configPath))
  application.replaceService(EditorColorsManager::class.java, manager, disposable)
  return manager
}

private fun countSchemeReloads(disposable: Disposable): AtomicInteger {
  val reloadCount = AtomicInteger()
  application.messageBus.connect(disposable).subscribe(EditorColorsManagerListener.TOPIC, object : EditorColorsManagerListener {
    override fun schemesReloaded() {
      reloadCount.incrementAndGet()
    }
  })
  return reloadCount
}

private suspend fun loadPlugins(registerExtensions: () -> Unit) {
  withContext(Dispatchers.EDT) {
    val listener = application.messageBus.syncPublisher(DynamicPluginListener.TOPIC)
    listener.beforePluginsLoaded()
    registerExtensions()
    listener.pluginsLoaded()
  }
}

private suspend fun unloadPlugins(unregisterExtensions: () -> Unit) {
  withContext(Dispatchers.EDT) {
    val listener = application.messageBus.syncPublisher(DynamicPluginListener.TOPIC)
    listener.beforePluginsUnloaded()
    unregisterExtensions()
    listener.pluginsUnloaded()
  }
}

private fun registerBundledColorScheme(parentDisposable: Disposable) {
  val scheme = BundledSchemeEP()
  scheme.path = "themes/expUI/expUI_lightScheme"
  BundledColorSchemeEPName.point.registerExtension(scheme, testPluginDescriptor(), parentDisposable)
}

private fun registerTheme(parentDisposable: Disposable) {
  val theme = UIThemeProvider()
  theme.id = "test.theme"
  theme.path = "/themes/test.theme.json"
  UIThemeProvider.EP_NAME.point.registerExtension(theme, testPluginDescriptor(), parentDisposable)
}

private fun testPluginDescriptor(): PluginDescriptor {
  return DefaultPluginDescriptor(PluginId.getId("test.color.schemes"), LafDynamicPluginManagerTest::class.java.classLoader)
}
