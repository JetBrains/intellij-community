// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.data.WizardPlugin
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.jetbrains.rd.util.lifetime.Lifetime
import javax.swing.JComponent
import javax.swing.JLabel

class WizardPluginPane(plugin: WizardPlugin, lifetime: Lifetime) {
  private lateinit var checkBox: JBCheckBox
  private lateinit var progress: JLabel
  private lateinit var error: JLabel

  private val controls = listOf<JComponent>(checkBox, progress, error)

  val pane = panel {
    row {
      checkBox = checkBox("").selected(plugin.state.value == WizardPlugin.State.CHECKED).onChanged { cb ->
        plugin.state.set(if (cb.isSelected) WizardPlugin.State.CHECKED else WizardPlugin.State.UNCHECKED)
      }.component
      progress = icon(AnimatedIcon.Default.INSTANCE).component
      error = icon(com.intellij.icons.AllIcons.Ide.FatalError).component
      icon(plugin.icon)
      panel {
        row {
          text(plugin.name).customize(UnscaledGaps(0, 0, 0, 0))
        }
        row {
          comment(plugin.description).customize(
            UnscaledGaps(top = 3))
        }
      }
    }
  }

  init {
    plugin.state.advise(lifetime) {
      when (it) {
        WizardPlugin.State.UNCHECKED -> {
          setVisibleControl(checkBox)
          checkBox.isSelected = false
          checkBox.isEnabled = true
        }
        WizardPlugin.State.CHECKED -> {
          setVisibleControl(checkBox)
          checkBox.isSelected = true
          checkBox.isEnabled = true
        }
        WizardPlugin.State.INSTALLED -> {
          setVisibleControl(checkBox)
          checkBox.isSelected = true
          checkBox.isEnabled = false
        }
        WizardPlugin.State.ERROR -> {
          setVisibleControl(error)
        }
        WizardPlugin.State.IN_PROGRESS -> {
          setVisibleControl(progress)
        }
      }
    }
  }

  private fun setVisibleControl(component: JComponent) {
    component.isVisible = true

    controls.forEach {
      if (it != component) {
        it.isVisible = false
      }
    }
  }
}