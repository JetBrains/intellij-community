// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicator
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput

class KotlinApplicatorBasedModCommand<PSI : PsiElement, in INPUT : KotlinApplicatorInput>(
    target: PSI,
    @FileModifier.SafeFieldForPreview
    private val input: INPUT,
    @FileModifier.SafeFieldForPreview
    private val applicator: KotlinApplicator.ModCommandBased<PSI, INPUT>,
) : PsiUpdateModCommandAction<PSI>(target),
    ReportingClassSubstitutor {

    override fun getFamilyName(): String =
        applicator.getFamilyName()

    override fun getPresentation(context: ActionContext, element: PSI): Presentation =
        Presentation.of(applicator.getActionName(element, input))

    override fun invoke(context: ActionContext, element: PSI, updater: ModPsiUpdater) {
        applicator.applyTo(element, input, context, updater)
    }

    override fun getSubstitutedClass(): Class<*> = applicator.javaClass
}