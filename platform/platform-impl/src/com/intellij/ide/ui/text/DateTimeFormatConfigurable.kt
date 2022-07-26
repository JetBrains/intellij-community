// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.text

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.text.CustomJBDateTimeFormatter
import com.intellij.util.text.DateTimeFormatManager
import com.intellij.util.text.JBDateFormat
import java.util.*
import javax.swing.JEditorPane
import javax.swing.event.DocumentEvent

/**
 * @author Konstantin Bulenkov
 */
class DateTimeFormatConfigurable : BoundSearchableConfigurable(
  IdeBundle.message("date.time.format.configurable"),
  "ide.date.format"
), NoScroll {

  private lateinit var dateFormatField: Cell<JBTextField>
  private lateinit var use24HourCheckbox: Cell<JBCheckBox>
  private lateinit var datePreviewField: Cell<JEditorPane>

  override fun createPanel(): DialogPanel {
    val settings = DateTimeFormatManager.getInstance()
    return panel {
      lateinit var overrideSystemDateFormatting: Cell<JBCheckBox>

      row {
        overrideSystemDateFormatting = checkBox(IdeBundle.message("date.format.override.system.date.and.time.format"))
          .bindSelected(
            { settings.isOverrideSystemDateFormat },
            { settings.isOverrideSystemDateFormat = it })
      }

      indent {
        row(IdeBundle.message("date.format.date.format")) {
          dateFormatField = textField()
            .bindText({ settings.dateFormatPattern },
                      { settings.dateFormatPattern = it })
            .columns(16)
            .applyToComponent {
              document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                  updateCommentField()
                }
              })
            }
          browserLink(IdeBundle.message("date.format.date.patterns"),
                      "https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html")
        }

        row {
          use24HourCheckbox = checkBox(IdeBundle.message("date.format.24.hours"))
            .bindSelected({ settings.isUse24HourTime },
                          { settings.isUse24HourTime = it })
            .applyToComponent {
              addChangeListener { updateCommentField() }
            }
        }

        row {
          datePreviewField = comment("", maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
        }
      }.enabledIf(overrideSystemDateFormatting.selected)

      row {
        checkBox(IdeBundle.message("date.format.pretty"))
          .bindSelected(
            { settings.isPrettyFormattingAllowed },
            { settings.isPrettyFormattingAllowed = it })
          .comment(IdeBundle.message("date.format.relative"))
      }.topGap(TopGap.SMALL)

      updateCommentField()

      onApply {
        JBDateFormat.invalidateCustomFormatter()
        LafManager.getInstance().updateUI()
      }
    }
  }

  private fun updateCommentField() {
    val formatter = CustomJBDateTimeFormatter(dateFormatField.component.text, use24HourCheckbox.component.isSelected)
    val calendar = Calendar.getInstance()
    calendar.set(1999, 11, 31, 23, 59, 59)
    datePreviewField.text(formatter.formatDateTime(calendar.time))
  }
}
