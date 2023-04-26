// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps

abstract class BoundCompositeConfigurable<T : UnnamedConfigurable>(
  @NlsContexts.ConfigurableName displayName: String,
  helpTopic: String? = null
) : BoundConfigurable(displayName, helpTopic) {
  abstract fun createConfigurables(): List<T>

  private val lazyConfigurables: Lazy<List<T>> = lazy { createConfigurables() }

  val configurables get() = lazyConfigurables.value
  private val plainConfigurables get() = lazyConfigurables.value.filter { it !is UiDslConfigurable && it !is UiDslUnnamedConfigurable }

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
      for (configurable in configurables) {
        configurable.disposeUIResources()
      }
    }
  }

  protected fun Panel.appendDslConfigurable(configurable: UnnamedConfigurable) {
    if (configurable is UiDslUnnamedConfigurable) {
      val builder = this
      with(configurable) {
        builder.createContent()
      }
    }
    else {
      val panel = configurable.createComponent()
      if (panel != null) {
        if (panel.layout !is GridLayout) {
          panel.putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
        }
        row {
          cell(panel)
            .align(AlignX.FILL)
        }
      }
    }
  }
}

abstract class BoundCompositeSearchableConfigurable<T : UnnamedConfigurable>(@NlsContexts.ConfigurableName displayName: String, helpTopic: String, private val _id: String = helpTopic)
  : BoundCompositeConfigurable<T>(displayName, helpTopic), SearchableConfigurable {
  override fun getId(): String = _id
}
