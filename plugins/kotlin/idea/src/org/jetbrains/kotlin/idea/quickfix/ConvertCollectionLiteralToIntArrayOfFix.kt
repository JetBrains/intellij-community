// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters1
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern

/**
 * Tests:
 * [org.jetbrains.kotlin.idea.quickfix.QuickFixTestGenerated.ConvertCollectionLiteralToIntArrayOf]
 */
class ConvertCollectionLiteralToIntArrayOfFix(element: KtCollectionLiteralExpression) :
    KotlinQuickFixAction<KtCollectionLiteralExpression>(element) {

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        element.text.takeIf { it.first() == '[' && it.last() == ']' }?.drop(1)?.dropLast(1)?.let { content ->
            element.replace(KtPsiFactory(project).createExpressionByPattern("intArrayOf($0)", content))
        }
    }

    override fun getText(): String = KotlinBundle.message("replace.with.arrayof")
    override fun getFamilyName(): String = text

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? = diagnostic
            .takeIf { "Collection literals outside of annotations" == (it as? DiagnosticWithParameters1<*, *>)?.a }
            ?.psiElement
            ?.let { it as? KtCollectionLiteralExpression }
            ?.let(::ConvertCollectionLiteralToIntArrayOfFix)
    }
}
