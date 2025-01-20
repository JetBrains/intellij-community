// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElement
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isCallee
import org.jetbrains.kotlin.psi.psiUtil.parents
import java.util.*
import kotlin.math.min

abstract class AbstractRenameUnresolvedReferenceFix(element: KtNameReferenceExpression) : KotlinQuickFixAction<KtNameReferenceExpression>(element) {
    companion object {
        private val INPUT_VARIABLE_NAME = "INPUT_VAR"
        private val OTHER_VARIABLE_NAME = "OTHER_VAR"
    }

    private class ReferenceNameExpression(
        private val items: Array<out LookupElement>,
        private val originalReferenceName: String
    ) : Expression() {
        init {
            Arrays.sort(items, HammingComparator(originalReferenceName) { lookupString })
        }

        override fun calculateResult(context: ExpressionContext) = TextResult(items.firstOrNull()?.lookupString ?: originalReferenceName)

        override fun calculateQuickResult(context: ExpressionContext) = null

        override fun calculateLookupItems(context: ExpressionContext) = if (items.size <= 1) null else items
    }

    override fun getText() = QuickFixBundle.message("rename.wrong.reference.text")

    override fun getFamilyName() = QuickFixBundle.message("rename.wrong.reference.family")

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        return editor != null && element.getStrictParentOfType<KtTypeReference>() == null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        if (editor == null) return
        val patternExpression = element.getQualifiedElement() as? KtExpression ?: return

        val originalName = element.getReferencedName()
        val container = element.parents.firstOrNull { it is KtDeclarationWithBody || it is KtClassOrObject || it is KtFile } ?: return
        val occurrences = patternExpression.findOccurrences(container as KtElement, element.isCallee())

        val lookupItems = patternExpression.getTargetCandidates(element)

        val nameExpression = ReferenceNameExpression(lookupItems, originalName)

        val builder = TemplateBuilderImpl(container)
        occurrences.forEach {
            if (it != element) {
                builder.replaceElement(it.getReferencedNameElement(), OTHER_VARIABLE_NAME, INPUT_VARIABLE_NAME, false)
            } else {
                builder.replaceElement(it.getReferencedNameElement(), INPUT_VARIABLE_NAME, nameExpression, true)
            }
        }

        editor.caretModel.moveToOffset(container.startOffset)
        if (file.isPhysical) {
            runWriteAction {
                TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate())
            }
        }
    }

    abstract fun KtExpression.findOccurrences(container: KtElement, isCallee: Boolean): List<KtNameReferenceExpression>
    abstract fun KtExpression.getTargetCandidates(element: KtNameReferenceExpression): Array<LookupElementBuilder>

    override fun startInWriteAction(): Boolean = false
}


private class HammingComparator<T>(private val referenceString: String, private val asString: T.() -> String) : Comparator<T> {
    private fun countDifference(s1: String): Int {
        return (0..min(s1.lastIndex, referenceString.lastIndex)).count { s1[it] != referenceString[it] }
    }

    override fun compare(lookupItem1: T, lookupItem2: T): Int {
        return countDifference(lookupItem1.asString()) - countDifference(lookupItem2.asString())
    }
}
