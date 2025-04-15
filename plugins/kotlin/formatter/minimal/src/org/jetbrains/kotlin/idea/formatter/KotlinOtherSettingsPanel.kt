// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import javax.swing.JCheckBox
import kotlin.reflect.KMutableProperty1

class KotlinOtherSettingsPanel(settings: CodeStyleSettings) : CodeStyleAbstractPanel(KotlinLanguage.INSTANCE, null, settings) {

    private lateinit var cbTrailingComma: JCheckBox
    private val trailingCommaCheckboxes: MutableMap<JCheckBox, KMutableProperty1<KotlinCodeStyleSettings, Boolean>> = mutableMapOf()

    companion object {
        private val TRAILING_COMMA_NODE_TYPE_SETTINGS: Map<String, KMutableProperty1<KotlinCodeStyleSettings, Boolean>> = mapOf(
            KotlinBundle.message("formatter.checkbox.text.type.parameter.list") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_TYPE_PARAMETER_LIST,
            KotlinBundle.message("formatter.checkbox.text.destructuring.declaration") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_DESTRUCTURING_DECLARATION,
            KotlinBundle.message("formatter.checkbox.text.when.entry") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_WHEN_ENTRY,
            KotlinBundle.message("formatter.checkbox.text.function.literal") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_FUNCTION_LITERAL,
            KotlinBundle.message("formatter.checkbox.text.value.parameter.list") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_VALUE_PARAMETER_LIST,
            KotlinBundle.message("formatter.checkbox.text.context.receiver.list") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_CONTEXT_RECEIVER_LIST,
            KotlinBundle.message("formatter.checkbox.text.collection.literal.expression") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_COLLECTION_LITERAL_EXPRESSION,
            KotlinBundle.message("formatter.checkbox.text.type.argument.list") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_TYPE_ARGUMENT_LIST,
            KotlinBundle.message("formatter.checkbox.text.indices") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_INDICES,
            KotlinBundle.message("formatter.checkbox.text.value.argument.list") to KotlinCodeStyleSettings::ALLOW_TRAILING_COMMA_VALUE_ARGUMENT_LIST,
        )
    }

    private val panel: DialogPanel = panel {
        group(KotlinBundle.message("formatter.title.trailing.comma")) {
            row {
                cbTrailingComma = checkBox(KotlinBundle.message("formatter.checkbox.text.use.trailing.comma")).component
                cbTrailingComma.addActionListener {
                    trailingCommaCheckboxes.forEach { it.key.isEnabled = cbTrailingComma.isSelected }
                }
            }
            indent {
                for ((text, settingProperty) in TRAILING_COMMA_NODE_TYPE_SETTINGS) {
                    row {
                        val checkbox = checkBox(text).component
                        trailingCommaCheckboxes[checkbox] = settingProperty
                    }
                }
            }
        }
    }.apply {
        border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
    }

    override fun getRightMargin() = throw UnsupportedOperationException()

    override fun createHighlighter(scheme: EditorColorsScheme) = throw UnsupportedOperationException()

    override fun getFileType() = throw UnsupportedOperationException()

    override fun getPreviewText(): String? = null

    override fun apply(settings: CodeStyleSettings) {
        settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA = cbTrailingComma.isSelected
        for ((checkbox, property) in trailingCommaCheckboxes) {
            property.set(settings.kotlinCustomSettings, checkbox.isSelected)
        }
    }

    override fun isModified(settings: CodeStyleSettings): Boolean {
        return settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA != cbTrailingComma.isSelected ||
                trailingCommaCheckboxes.any { it.key.isSelected != it.value.get(settings.kotlinCustomSettings) }
    }

    override fun getPanel() = panel

    override fun resetImpl(settings: CodeStyleSettings) {
        cbTrailingComma.isSelected = settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA
        for ((checkbox, property) in trailingCommaCheckboxes) {
            checkbox.isSelected = property.get(settings.kotlinCustomSettings)
            checkbox.isEnabled = cbTrailingComma.isSelected
        }
    }

    override fun getTabTitle(): String = KotlinBundle.message("formatter.title.other")

}
