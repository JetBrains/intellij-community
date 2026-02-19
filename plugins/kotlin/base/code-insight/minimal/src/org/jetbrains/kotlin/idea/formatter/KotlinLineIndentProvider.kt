// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.lineIndent.KotlinIndentationAdjuster
import org.jetbrains.kotlin.idea.formatter.lineIndent.KotlinLangLineIndentProvider

class KotlinLineIndentProvider : KotlinLangLineIndentProvider() {
    override fun getLineIndent(project: Project, editor: Editor, language: Language?, offset: Int): String? =
        if (useFormatter)
            null
        else
            super.getLineIndent(project, editor, language, offset)

    override fun indentionSettings(editor: Editor): KotlinIndentationAdjuster = object : KotlinIndentationAdjuster {
        val settings = CodeStyle.getSettings(editor)
        val commonSettings: KotlinCommonCodeStyleSettings get() = settings.kotlinCommonSettings
        val customSettings: KotlinCodeStyleSettings get() = settings.kotlinCustomSettings

        override val alignWhenMultilineFunctionParentheses: Boolean
            get() = commonSettings.ALIGN_MULTILINE_METHOD_BRACKETS

        override val alignWhenMultilineBinaryExpression: Boolean
            get() = commonSettings.ALIGN_MULTILINE_BINARY_OPERATION

        override val continuationIndentInElvis: Boolean
            get() = customSettings.CONTINUATION_INDENT_IN_ELVIS

        override val continuationIndentForExpressionBodies: Boolean
            get() = customSettings.CONTINUATION_INDENT_FOR_EXPRESSION_BODIES

        override val alignMultilineParameters: Boolean
            get() = commonSettings.ALIGN_MULTILINE_PARAMETERS

        override val alignMultilineParametersInCalls: Boolean
            get() = commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS

        override val continuationIndentInArgumentLists: Boolean
            get() = customSettings.CONTINUATION_INDENT_IN_ARGUMENT_LISTS

        override val continuationIndentInParameterLists: Boolean
            get() = customSettings.CONTINUATION_INDENT_IN_PARAMETER_LISTS

        override val continuationIndentInIfCondition: Boolean
            get() = customSettings.CONTINUATION_INDENT_IN_IF_CONDITIONS

        override val continuationIndentForChainedCalls: Boolean
            get() = customSettings.CONTINUATION_INDENT_FOR_CHAINED_CALLS

        override val indentBeforeArrowOnNewLine: Boolean
            get() = customSettings.INDENT_BEFORE_ARROW_ON_NEW_LINE
    }

    companion object {
        @set:TestOnly
        var useFormatter: Boolean = false
    }
}