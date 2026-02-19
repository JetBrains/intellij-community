// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.Registry.Companion.get
import com.intellij.openapi.util.registry.Registry.Companion.stringValue
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import org.jetbrains.annotations.ApiStatus
import javax.swing.JFormattedTextField

@ApiStatus.Internal
class DataViewsConfigurableUi {

  companion object {
    const val DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY: String = "debugger.valueTooltipAutoShow"
    const val DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY: String = "debugger.valueTooltipAutoShowOnSelection"
  }

  private var valueTooltipDelayTextField = JFormattedTextField()

  private val panel = panel {
    val settings = XDebuggerSettingsManager.getInstance().dataViewSettings

    row {
      checkBox(XDebuggerBundle.message("setting.sort.alphabetically.label"))
        .bindSelected(settings::isSortValues, settings::setSortValues)
    }
    row {
      checkBox(XDebuggerBundle.message("setting.enable.auto.expressions.label"))
        .bindSelected(settings::isAutoExpressions, settings::setAutoExpressions)
    }
    group(XDebuggerBundle.message("data.views.configurable.panel.title")) {
      row {
        checkBox(XDebuggerBundle.message("settings.show.values.inline"))
          .bindSelected(settings::isShowValuesInline, settings::setShowValuesInline)
      }
      row {
        val showTooltip = checkBox(XDebuggerBundle.message("settings.show.value.tooltip"))
          .comment(XDebuggerBundle.message("settings.tooltip.label", stringValue("ide.forcedShowTooltip")))
          .gap(RightGap.SMALL)
          .bindSelected({ Registry.`is`(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY) },
                        { get(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_KEY).setValue(it) })
        cell(valueTooltipDelayTextField)
          .label(XDebuggerBundle.message("setting.value.tooltip.delay.label"))
          .bind(::getValueTooltipDelay, { it, value -> it.value = value },
                MutableProperty(settings::getValueLookupDelay, settings::setValueLookupDelay))
          .enabledIf(showTooltip.selected)
      }
      row {
        checkBox(XDebuggerBundle.message("settings.show.tooltip.on.selection"))
          .bindSelected({ Registry.`is`(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY) },
                        { get(DEBUGGER_VALUE_TOOLTIP_AUTO_SHOW_ON_SELECTION_KEY).setValue(it) })
      }
    }
  }

  init {
    UIUtil.configureNumericFormattedTextField(valueTooltipDelayTextField)
  }

  fun getComponent(): DialogPanel {
    return panel
  }

  private fun getValueTooltipDelay(textField: JFormattedTextField): Int {
    val value = textField.value
    return if (value is Number) value.toInt() else StringUtilRt.parseInt(value as String?, XDebuggerSettingsManager.DataViewSettings.DEFAULT_VALUE_TOOLTIP_DELAY)
  }
}
