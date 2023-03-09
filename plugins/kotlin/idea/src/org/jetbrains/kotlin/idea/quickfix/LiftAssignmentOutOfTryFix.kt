// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class LiftAssignmentOutOfTryFix(element: KtTryExpression) : KotlinQuickFixAction<KtTryExpression>(element) {
    override fun getFamilyName() = text

    override fun getText() = KotlinBundle.message("lift.assignment.out.of.try.expression")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        BranchedFoldingUtils.tryFoldToAssignment(element)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? = diagnostic.psiElement
            .parentsWithSelf.match(
                KtExpression::class,
                KtBinaryExpression::class,
                KtBlockExpression::class,
                KtCatchClause::class,
                last = KtTryExpression::class,
            )
            .takeIf { BranchedFoldingUtils.getFoldableAssignmentNumber(it) >= 1 }
            ?.let(::LiftAssignmentOutOfTryFix)
    }
}