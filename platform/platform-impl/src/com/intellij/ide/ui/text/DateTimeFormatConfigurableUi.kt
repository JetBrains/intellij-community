// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.text

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.JBDateFormat
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
class DateTimeFormatConfigurableUi(settings: DateTimeFormatManager) : ConfigurableUi<DateTimeFormatManager> {
  private val ui: DialogPanel

  init {
    ui = panel {
      lateinit var overrideSystemDateFormatting: Cell<JBCheckBox>

      row {
        overrideSystemDateFormatting = checkBox(IdeBundle.message("date.format.override.system.date.and.time.format"))
          .bindSelected(
            { settings.isOverrideSystemDateFormat },
            { settings.isOverrideSystemDateFormat = it })
      }

      indent {
        row(IdeBundle.message("date.format.date.format")) {
          textField()
            .bindText({ settings.dateFormatPattern },
              { settings.dateFormatPattern = it })
            .columns(16)
          browserLink(IdeBundle.message("date.format.date.patterns"), "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")
        }

        row {
          checkBox(IdeBundle.message("date.format.24.hours"))
            .bindSelected({ settings.isUse24HourTime },
              { settings.isUse24HourTime = it })
        }
      }.enabledIf(overrideSystemDateFormatting.selected)

      row {
        checkBox(IdeBundle.message("date.format.pretty"))
          .bindSelected(
            { settings.isPrettyFormattingAllowed },
            { settings.isPrettyFormattingAllowed = it })
          .comment(IdeBundle.message("date.format.relative"))
      }.topGap(TopGap.SMALL)
    }
  }

  override fun reset(settings: DateTimeFormatManager) = ui.reset()

  override fun isModified(settings: DateTimeFormatManager): Boolean = ui.isModified()

  @Throws(ConfigurationException::class)
  override fun apply(settings: DateTimeFormatManager) {
    ui.apply()
    JBDateFormat.invalidateCustomFormatter()
    LafManager.getInstance().updateUI()
  }

  override fun getComponent(): JComponent = ui
}
