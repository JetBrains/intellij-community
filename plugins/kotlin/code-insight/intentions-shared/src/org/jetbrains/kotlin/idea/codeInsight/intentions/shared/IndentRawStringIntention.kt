// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommand.nop
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

private const val QTS = "\"\"\""

class IndentRawStringIntention : PsiBasedModCommandAction<KtStringTemplateExpression>(KtStringTemplateExpression::class.java) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("indent.raw.string")

    private fun isAvailable(element: KtStringTemplateExpression): Boolean {
        if (!element.text.startsWith(QTS)) return false
        if (element.parents.any { it is KtAnnotationEntry || (it as? KtProperty)?.hasModifier(KtTokens.CONST_KEYWORD) == true }) return false
        if (element.getQualifiedExpressionForReceiver() != null) return false
        val entries = element.entries
        return !(entries.size <= 1 || entries.any { it.text.startsWith(" ") || it.text.startsWith("\t") })
    }

    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation? =
        if (isAvailable(element)) {
            super.getPresentation(context, element)
        } else {
            null
        }

    override fun perform(
        context: ActionContext,
        element: KtStringTemplateExpression
    ): ModCommand = if (isAvailable(element)) {
        ModCommand.psiUpdate(element) { e, updater ->
            val file = e.containingKtFile
            val project = file.project
            val indentOptions = CodeStyle.getIndentOptions(file)
            val parentIndent = CodeStyleManager.getInstance(project).getLineIndent(file, e.parent.startOffset) ?: ""
            val indent = if (indentOptions.USE_TAB_CHARACTER) "$parentIndent\t" else "$parentIndent${" ".repeat(indentOptions.INDENT_SIZE)}"

            val newString = buildString {
                val maxIndex = e.entries.size - 1
                e.entries.forEachIndexed { index, entry ->
                    if (index == 0) append("\n$indent")
                    append(entry.text)
                    if (entry.text == "\n") append(indent)
                    if (index == maxIndex) append("\n$indent")
                }
            }

            val psiFactory = KtPsiFactory(project)
            val expression = psiFactory.createExpression("$QTS${newString}$QTS.trimIndent()")
            e.replace(expression)
        }
    } else {
        nop()
    }
}