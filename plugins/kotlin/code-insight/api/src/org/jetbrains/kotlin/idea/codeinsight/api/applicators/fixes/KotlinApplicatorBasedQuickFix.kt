// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.BaseKotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile

class KotlinApplicatorBasedQuickFix<PSI : PsiElement, in INPUT : KotlinApplicatorInput>(
    target: PSI,
    @FileModifier.SafeFieldForPreview
    private val input: INPUT,
    @FileModifier.SafeFieldForPreview
    val applicator: BaseKotlinApplicator<PSI, INPUT>,
) : KotlinQuickFixAction<PSI>(target), ReportingClassSubstitutor {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        if (applicator.isApplicableByPsi(element, project) && input.isValidFor(element)) {
            applicator.applyTo(element, input, project, editor)
        }
    }

    override fun getText(): String {
        val element = element ?: return familyName
        return if (input.isValidFor(element)) {
            applicator.getActionName(element, input)
        } else {
            applicator.getFamilyName()
        }
    }

    override fun getFamilyName(): String =
        applicator.getFamilyName()

    override fun startInWriteAction(): Boolean {
        return applicator.startInWriteAction()
    }

    override fun getSubstitutedClass(): Class<*> =
      (applicator as? ReportingClassSubstitutor)?.substitutedClass ?: applicator.javaClass
}