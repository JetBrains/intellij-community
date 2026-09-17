// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SuspiciousPackagePrivateAccess")

package com.intellij.openapi.options.newEditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.concurrency.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

@TestApplication
@Timeout(30)
internal class ConfigurableEditorFocusTest {
  @Test
  fun `editor uses the wrapped configurable focus after loading`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val configurable = createWrappedConfigurable()
    val editor = withContext(Dispatchers.UI) {
      ConfigurableEditor(disposable).apply { init(null, false) }
    }

    withContext(Dispatchers.UI) { editor.select(configurable) }.await()

    val (preferred, expected) = withContext(Dispatchers.UI) {
      val wrapped = configurable.rawConfigurable as PreferredFocusConfigurable
      editor.getPreferredFocusedComponent() to wrapped.preferredFocusedComponent
    }
    assertThat(preferred).isSameAs(expected)
  }

  private fun createWrappedConfigurable(): ConfigurableWrapper {
    val descriptor = DefaultPluginDescriptor(
      PluginId.getId("com.intellij.openapi.options.configurableEditorFocusTest"),
      ConfigurableEditorFocusTest::class.java.classLoader,
    )
    val ep = ConfigurableEP<Configurable>(descriptor).apply {
      id = "focused"
      displayName = "Focused"
      instanceClass = PreferredFocusConfigurable::class.java.name
    }
    return ConfigurableWrapper.wrapConfigurable(ep) as ConfigurableWrapper
  }

  private class PreferredFocusConfigurable : Configurable {
    private var focusComponent: JComponent? = null

    override fun getDisplayName(): String = "Focused"

    override fun createComponent(): JComponent {
      val textField = JTextField()
      focusComponent = textField
      return JPanel().apply { add(textField) }
    }

    override fun getPreferredFocusedComponent(): JComponent? = focusComponent

    override fun isModified(): Boolean = false

    override fun apply() = Unit
  }
}
