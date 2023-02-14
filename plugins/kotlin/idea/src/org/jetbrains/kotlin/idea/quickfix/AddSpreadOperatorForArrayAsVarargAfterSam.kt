// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

object AddSpreadOperatorForArrayAsVarargAfterSamFixFactory : KotlinSingleIntentionActionFactory() {
    public override fun createAction(diagnostic: Diagnostic): IntentionAction {
        val diagnosticWithParameters = Errors.TYPE_INFERENCE_CANDIDATE_WITH_SAM_AND_VARARG.cast(diagnostic)
        val argument = diagnosticWithParameters.psiElement

        return AddSpreadOperatorForArrayAsVarargAfterSamFix(argument)
    }
}

class AddSpreadOperatorForArrayAsVarargAfterSamFix(element: PsiElement) : KotlinQuickFixAction<PsiElement>(element) {
    override fun getFamilyName() = KotlinBundle.message("fix.add.spread.operator.after.sam")

    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return

        element.addBefore(KtPsiFactory(project).createStar(), element.firstChild)
    }
}