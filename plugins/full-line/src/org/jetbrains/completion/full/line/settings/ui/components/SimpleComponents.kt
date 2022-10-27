package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.lang.Language
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.layout.*
import org.jetbrains.completion.full.line.models.ModelType
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import org.jetbrains.completion.full.line.settings.ui.LANGUAGE_CHECKBOX_NAME
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.reflect.KMutableProperty0

// For some reason method intTextField() in com/intellij/ui/layout/Cell.kt
// throws java.lang.LinkageError: loader constraint violation: when resolving method,
// But it's copy works fine :/
@Deprecated("Use Kotlin UI DSL 2 instead, see com.intellij.ui.dsl.builder.Row.intTextField")
fun Cell.intTextFieldFixed(binding: PropertyBinding<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JTextField> {
  return textField(
    { binding.get().toString() },
    { value -> value.toIntOrNull()?.let { intValue -> binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue) } },
    columns
  ).withValidationOnInput {
    val value = it.text.toIntOrNull()
    if (value == null)
      error(message("full.line.int.text.field.error.valid.number"))
    else if (range != null && value !in range)
      error(message("full.line.int.text.field.error.range.number", range.first, range.last))
    else null
  }
}

@Deprecated("Use Kotlin UI DSL 2, see another doubleTextField in this file")
fun Cell.doubleTextField(binding: PropertyBinding<Double>, columns: Int? = null, range: IntRange? = null): CellBuilder<JTextField> {
  return textField(
    { binding.get().toString() },
    { value ->
      value.toDoubleOrNull()
        ?.let { intValue -> binding.set(range?.let { intValue.coerceIn(it.first.toDouble(), it.last.toDouble()) } ?: intValue) }
    },
    columns
  ).withValidationOnInput {
    val value = it.text.toDoubleOrNull()
    if (value == null)
      error(message("full.line.double.text.field.error.valid.number"))
    else if (range != null && (value < range.first || value > range.last))
      error(message("full.line.double.text.field.error.range.number", range.first, range.last))
    else null
  }
}

fun com.intellij.ui.dsl.builder.Row.doubleTextField(prop: KMutableProperty0<Double>,
                                                    range: IntRange? = null): com.intellij.ui.dsl.builder.Cell<JTextField> {
  return doubleTextField(prop.toMutableProperty(), range)
}

fun com.intellij.ui.dsl.builder.Row.doubleTextField(prop: MutableProperty<Double>,
                                                    range: IntRange? = null): com.intellij.ui.dsl.builder.Cell<JTextField> {
  return textField()
    .bindText({ prop.get().toString() },
              { value ->
                value.toDoubleOrNull()
                  ?.let { intValue -> prop.set(range?.let { intValue.coerceIn(it.first.toDouble(), it.last.toDouble()) } ?: intValue) }
              })
    .validationOnInput {
      val value = it.text.toDoubleOrNull()
      if (value == null)
        error(message("full.line.double.text.field.error.valid.number"))
      else if (range != null && (value < range.first || value > range.last))
        error(message("full.line.double.text.field.error.range.number", range.first, range.last))
      else null
    }
}

@Deprecated("Use Kotlin UI DSL 2 instead, see com.intellij.ui.dsl.builder.Row.intTextField")
fun Cell.intTextFieldFixed(prop: KMutableProperty0<Int>, columns: Int? = null, range: IntRange? = null): CellBuilder<JTextField> {
  return intTextFieldFixed(prop.toBinding(), columns, range)
}

@Deprecated("Use Kotlin UI DSL 2")
fun Row.separatorRow(): Row {
  return row {
    component(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
  }
}

fun languageComboBox(langPanel: DialogPanel): ComboBox<String> {
  return ComboBox<String>().apply {
    renderer = listCellRenderer { langId, _, _ ->
      text = Language.findLanguageByID(langId)?.displayName ?: ""
    }
    addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        (langPanel.layout as CardLayout).show(langPanel, it.item.toString())
      }
    }
  }
}

fun modelTypeComboBox(langPanel: DialogPanel): ComboBox<ModelType> {
  return ComboBox<ModelType>().apply {
    renderer = listCellRenderer { value, _, _ ->
      @NlsSafe val valueName = value.name
      text = valueName
      icon = value.icon
    }
    addItemListener {
      if (it.stateChange == ItemEvent.SELECTED) {
        (langPanel.layout as CardLayout).show(langPanel, it.item.toString())
      }
    }
  }
}

@Deprecated("Use Row.languageCheckBox method")
fun languageCheckBox(language: Language, biggestLang: String?): JBCheckBox {
  return JBCheckBox(language.displayName, MLServerCompletionSettings.getInstance().getLangState(language).enabled).apply {
    minimumSize = Dimension(36 + getFontMetrics(font).stringWidth(biggestLang ?: language.id), preferredSize.height)
    name = LANGUAGE_CHECKBOX_NAME
  }
}

fun com.intellij.ui.dsl.builder.Row.languageCheckBox(language: Language,
                                                     biggestLang: String?): com.intellij.ui.dsl.builder.Cell<JBCheckBox> {
  return checkBox(language.displayName).applyToComponent {
    isSelected = MLServerCompletionSettings.getInstance().getLangState(language).enabled
    minimumSize = Dimension(36 + getFontMetrics(font).stringWidth(biggestLang ?: language.id), preferredSize.height)
    name = LANGUAGE_CHECKBOX_NAME
  }
}

@Deprecated("Use Kotlin UI DSL 2, see another loadingStatus in this file")
fun Cell.loadingStatus(loadingIcon: LoadingComponent): List<CellBuilder<JComponent>> {
  return listOf(
    component(loadingIcon.loadingIcon),
    component(loadingIcon.statusText),
  )
}

fun com.intellij.ui.dsl.builder.Row.loadingStatus(loadingIcon: LoadingComponent): List<com.intellij.ui.dsl.builder.Cell<JComponent>> {
  return listOf(
    cell(loadingIcon.loadingIcon),
    cell(loadingIcon.statusText),
  )
}

