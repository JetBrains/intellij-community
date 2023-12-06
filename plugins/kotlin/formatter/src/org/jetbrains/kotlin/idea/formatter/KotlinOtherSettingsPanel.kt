// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import javax.swing.JCheckBox

class KotlinOtherSettingsPanel(settings: CodeStyleSettings) : CodeStyleAbstractPanel(KotlinLanguage.INSTANCE, null, settings) {

    private lateinit var cbTrailingComma: JCheckBox
    private val panel: DialogPanel = panel {
        group(KotlinBundle.message("formatter.title.trailing.comma")) {
            row {
                cbTrailingComma = checkBox(KotlinBundle.message("formatter.checkbox.text.use.trailing.comma")).component
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
    }

    override fun isModified(settings: CodeStyleSettings): Boolean {
        return settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA != cbTrailingComma.isSelected
    }

    override fun getPanel() = panel

    override fun resetImpl(settings: CodeStyleSettings) {
        cbTrailingComma.isSelected = settings.kotlinCustomSettings.ALLOW_TRAILING_COMMA
    }

    override fun getTabTitle(): String = KotlinBundle.message("formatter.title.other")

}
