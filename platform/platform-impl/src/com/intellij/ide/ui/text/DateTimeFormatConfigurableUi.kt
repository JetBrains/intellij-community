// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.text

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.layout.*
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.JBDateFormat
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTextField

/**
 * @author Konstantin Bulenkov
 */
class DateTimeFormatConfigurableUi(settings: DateTimeFormatManager) : ConfigurableUi<DateTimeFormatManager> {
  private val ui: JComponent
  private lateinit var usePrettyFormatting: JCheckBox
  private lateinit var link: HyperlinkLabel
  private lateinit var overrideSystemDateFormatting: JCheckBox
  private lateinit var use24HourTime: JCheckBox
  private lateinit var pattern: JTextField

  init {
    ui = panel {
      row {
        overrideSystemDateFormatting = checkBox("Override system date and time format",
                                                { settings.isOverrideSystemDateFormat },
                                                { settings.isOverrideSystemDateFormat = it }).component
        row("Date format:") {
          cell {
            pattern = textField({ settings.dateFormatPattern },
                                { settings.dateFormatPattern = it },
                                16).component
            browserLink("Date patterns", "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")
          }
        }.enableIf(overrideSystemDateFormatting.selected)

        row {
          use24HourTime = checkBox("Use 24-hour time", { settings.isUse24HourTime },
                                   { settings.isUse24HourTime = it }).component
        }.enableIf(overrideSystemDateFormatting.selected)

      }
      row {
        usePrettyFormatting = checkBox("Use pretty formatting",
                                       { settings.isPrettyFormattingAllowed },
                                       { settings.isPrettyFormattingAllowed = it })
          .comment("Replace numeric date with <i>Today</i>, <i>Yesterday</i>, and <i>10 minutes ago</i>").component
      }
    }
  }

  override fun reset(settings: DateTimeFormatManager) {
    usePrettyFormatting.isSelected = settings.isPrettyFormattingAllowed
  }

  override fun isModified(settings: DateTimeFormatManager): Boolean {
    return usePrettyFormatting.isSelected != settings.isPrettyFormattingAllowed
           || overrideSystemDateFormatting.isSelected != settings.isOverrideSystemDateFormat
           || !StringUtil.equals(pattern.text, settings.dateFormatPattern)
           || use24HourTime.isSelected != settings.isUse24HourTime
  }

  @Throws(ConfigurationException::class)
  override fun apply(settings: DateTimeFormatManager) {
    settings.isPrettyFormattingAllowed = usePrettyFormatting.isSelected
    settings.isOverrideSystemDateFormat = overrideSystemDateFormatting.isSelected
    settings.dateFormatPattern = pattern.text
    settings.isUse24HourTime = use24HourTime.isSelected
    JBDateFormat.invalidateCustomFormatter()
    LafManager.getInstance().updateUI()
  }

  override fun getComponent(): JComponent {
    return ui
  }
}
