// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.codeStyle.OptionTreeWithPreviewPanel
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.ui.border.CustomLineBorder
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The "KDoc" tab of the Kotlin code style settings.
 * Mostly ported from `com.intellij.application.options.JavaDocFormattingPanel`.
 */
internal class KDocFormattingPanel(settings: CodeStyleSettings) : OptionTreeWithPreviewPanel(settings) {

    private lateinit var enableCheckBox: JCheckBox
    private val kdocPanel = JPanel(BorderLayout())

    init {
        init()
    }

    override fun init() {
        super.init()

        enableCheckBox = JCheckBox(KotlinBundle.message("formatter.checkbox.text.enable.kdoc.formatting"))
        enableCheckBox.addActionListener { update() }

        myPanel.border = CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0)
        kdocPanel.add(BorderLayout.CENTER, myPanel)
        kdocPanel.add(enableCheckBox, BorderLayout.NORTH)
    }

    override fun getSettingsType(): LanguageCodeStyleSettingsProvider.SettingsType =
        LanguageCodeStyleSettingsProvider.SettingsType.LANGUAGE_SPECIFIC

    override fun getPanel(): JComponent = kdocPanel

    override fun initTables() {
        initBooleanField(
            "WRAP_COMMENTS",
            KotlinBundle.message("formatter.checkbox.text.wrap.at.right.margin"),
            getOtherGroup(),
        )
        initCustomOptions(getOtherGroup())
    }

    override fun getRightMargin(): Int = 47

    override fun getPreviewText(): String =
        """
        /**
         * This is a declaration description that is long enough to exceed the right margin.
         *
         * Another paragraph of the description placed after a blank line, referring to [Foo].
         *
         * An indented code block is never reflowed:
         *
         *     val a = 1
         *     println(a)
         *
         * @param i short named parameter description
         * @param longParameterName long named parameter description that is long enough to wrap
         * @return return description.
         * @throws IllegalStateException description.
         */
        fun sampleMethod(i: Int, longParameterName: Int): String = ""

        /** One-line comment */
        fun sampleMethod2(): String = ""
        """.trimIndent()

    override fun apply(settings: CodeStyleSettings) {
        super.apply(settings)
        settings.kotlinCustomSettings.ENABLE_KDOC_FORMATTING = enableCheckBox.isSelected
    }

    override fun resetImpl(settings: CodeStyleSettings) {
        super.resetImpl(settings)
        enableCheckBox.isSelected = settings.kotlinCustomSettings.ENABLE_KDOC_FORMATTING
        update()
    }

    override fun isModified(settings: CodeStyleSettings): Boolean =
        super.isModified(settings) || enableCheckBox.isSelected != settings.kotlinCustomSettings.ENABLE_KDOC_FORMATTING

    override fun getFileType(): FileType = KotlinFileType.INSTANCE

    override fun customizeSettings() {
        LanguageCodeStyleSettingsProvider.forLanguage(KotlinLanguage.INSTANCE)?.customizeSettings(this, settingsType)
    }

    override fun getTabTitle(): String = KotlinBundle.message("formatter.title.kdoc")

    override fun getDefaultLanguage(): Language = KotlinLanguage.INSTANCE

    private fun update() {
        setEnabledRecursively(panel, enableCheckBox.isSelected)
        enableCheckBox.isEnabled = true
    }

    companion object {
        @NlsContexts.Label
        fun getOtherGroup(): String = KotlinBundle.message("formatter.title.other")

        private fun setEnabledRecursively(component: JComponent, enabled: Boolean) {
            component.isEnabled = enabled
            for (child in component.components) {
                if (child is JComponent) setEnabledRecursively(child, enabled)
            }
        }
    }
}
