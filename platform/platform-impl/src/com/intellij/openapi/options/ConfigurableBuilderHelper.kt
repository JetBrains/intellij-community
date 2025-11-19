// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class ConfigurableBuilderHelper {
  companion object {

    @JvmStatic
    @ApiStatus.Internal
    fun createBeanPanel(beanConfigurable: BeanConfigurable<*>, components: List<JComponent>): DialogPanel {
      return panel {
        appendBeanConfigurableContent(beanConfigurable, components)
      }
    }

    @JvmStatic
    @ApiStatus.Internal
    fun integrateBeanPanel(rootPanel: Panel, beanConfigurable: BeanConfigurable<*>, components: List<JComponent>) {
      rootPanel.appendBeanConfigurableContent(beanConfigurable, components)
      rootPanel.onApply { beanConfigurable.apply() }
      rootPanel.onIsModified { beanConfigurable.isModified() }
      rootPanel.onReset { beanConfigurable.reset() }
    }

    private fun Panel.appendBeanConfigurableContent(beanConfigurable: BeanConfigurable<*>, components: List<JComponent>) {
      val title = beanConfigurable.title

      if (title != null) {
        group(title) {
          appendBeanFields(components)
        }
      }
      else {
        appendBeanFields(components)
      }
    }

    private fun Panel.appendBeanFields(components: List<JComponent>) {
      for (component in components) {
        row {
          cell(component)
        }
      }
    }
  }
}
