// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*

abstract class BoundCompositeConfigurable<T : UnnamedConfigurable>(
  @NlsContexts.ConfigurableName displayName: String,
  helpTopic: String? = null
) : BoundConfigurable(displayName, helpTopic) {
  abstract fun createConfigurables(): List<T>

  private val lazyConfigurables: Lazy<List<T>> = lazy { createConfigurables() }

  protected val configurables get() = lazyConfigurables.value
  private val plainConfigurables get() = lazyConfigurables.value.filter { it !is UiDslConfigurable }

  override fun isModified(): Boolean {
    return super.isModified() || plainConfigurables.any { it.isModified }
  }

  override fun reset() {
    super.reset()
    for (configurable in plainConfigurables) {
      configurable.reset()
    }
  }

  override fun apply() {
    super.apply()
    for (configurable in plainConfigurables) {
      configurable.apply()
    }
  }

  override fun disposeUIResources() {
    super.disposeUIResources()
    if (lazyConfigurables.isInitialized()) {
      for (configurable in plainConfigurables) {
        configurable.disposeUIResources()
      }
    }
  }

  protected fun RowBuilder.appendDslConfigurableRow(configurable: UnnamedConfigurable) {
    if (configurable is UiDslConfigurable) {
      val builder = this
      with(configurable) {
        builder.createComponentRow()
      }
    }
    else {
      val panel = configurable.createComponent()
      if (panel != null) {
        row {
          component(panel)
        }
      }
    }
  }
}

abstract class BoundCompositeSearchableConfigurable<T : UnnamedConfigurable>(@NlsContexts.ConfigurableName displayName: String, helpTopic: String, private val _id: String = helpTopic)
  : BoundCompositeConfigurable<T>(displayName, helpTopic), SearchableConfigurable {
  override fun getId(): String = _id
}
