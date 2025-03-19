// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.text.DateTimeFormatManager
import org.jetbrains.annotations.ApiStatus
import java.text.SimpleDateFormat
import javax.swing.JEditorPane
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
class DateTimeFormatConfigurable :
  BoundSearchableConfigurable(IdeBundle.message("date.time.format.configurable"), helpTopic = "reference.date.formats", _id = "ide.date.format"),
  NoScroll
{
  private lateinit var dateFormatField: Cell<JBTextField>
  private lateinit var use24HourCheckbox: Cell<JBCheckBox>
  private lateinit var datePreviewField: Cell<JEditorPane>

  override fun createPanel(): DialogPanel {
    val settings = DateTimeFormatManager.getInstance()
    return panel {
      lateinit var overrideSystemDateFormatting: Cell<JBCheckBox>

      row {
        overrideSystemDateFormatting = checkBox(IdeBundle.message("date.format.override.system.date.and.time.format"))
          .bindSelected(settings::isOverrideSystemDateFormat, settings::setOverrideSystemDateFormat)
      }

      indent {
        row(IdeBundle.message("date.format.date.format")) {
          dateFormatField = textField()
            .bindText(settings::getDateFormatPattern, settings::setDateFormatPattern)
            .columns(16)
            .validationOnInput { field ->
              validateDatePattern(field.text)?.let { error(it) }
            }
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
            .bindSelected(settings::isUse24HourTime, settings::setUse24HourTime)
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
          .bindSelected(settings::isPrettyFormattingAllowed, settings::setPrettyFormattingAllowed)
          .comment(IdeBundle.message("date.format.relative"))
      }.topGap(TopGap.SMALL)

      updateCommentField()

      onApply {
        settings.resetFormats()
        LafManager.getInstance().updateUI()
      }
    }
  }

  private fun validateDatePattern(pattern: String): @NlsContexts.DialogMessage String? {
    try {
      SimpleDateFormat(pattern)
    }
    catch (e: IllegalArgumentException) {
      return IdeBundle.message("date.format.error.invalid.pattern", e.message)
    }

    if (pattern.contains("'")) return null // escaped text - assume user knows what they're doing
    if (StringUtil.containsAnyChar(pattern, "aHhKkmSs")) {
      return IdeBundle.message("date.format.error.contains.time.pattern")
    }
    return null
  }

  private fun updateCommentField() {
    val text = try {
      val timeFmt = if (use24HourCheckbox.component.isSelected) DateFormatUtil.TIME_SHORT_24H else DateFormatUtil.TIME_SHORT_12H
      SimpleDateFormat("${dateFormatField.component.text} ${timeFmt}")
        .format(DateFormatUtil.getSampleDateTime())
    }
    catch (e: IllegalArgumentException) {
      IdeBundle.message("date.format.error.invalid.pattern", e.message)
    }
    datePreviewField.text(StringUtil.escapeXmlEntities(text))
  }
}
