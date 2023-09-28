// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel

interface UiDslUnnamedConfigurable : UnnamedConfigurable {

  /**
   * Creates content of configurable. Use [Panel.group] or [Panel.panel] as first element to avoid interference with parent grid and
   * its content
   */
  fun Panel.createContent()

  /**
   * Methods [isModified], [reset], [apply] and [disposeUIResources] are final because they are never called for [UiDslUnnamedConfigurable]
   */
  abstract class Simple : DslConfigurableBase(), UiDslUnnamedConfigurable {

    final override fun createPanel(): DialogPanel {
      return panel {
        createContent()
      }
    }

    final override fun isModified(): Boolean = super.isModified()
    final override fun reset(): Unit = super<DslConfigurableBase>.reset()
    final override fun apply(): Unit = super.apply()
    final override fun disposeUIResources(): Unit = super<DslConfigurableBase>.disposeUIResources()
  }
}
