// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.base.psi.mustHaveValOrVar
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.addValVarToConstructorParameter
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration

class AddValVarToConstructorParameterActionIntention : KotlinApplicableModCommandAction.Simple<KtParameter>(KtParameter::class) {

    override fun getFamilyName(): String = KotlinBundle.message("add.val.var.to.primary.constructor.parameter")

    override fun getPresentation(context: ActionContext, element: KtParameter): Presentation {
        val actionName = KotlinBundle.message("add.val.var.to.parameter.0", element.name ?: "")
        return Presentation.of(actionName)
    }

    override fun isApplicableByPsi(element: KtParameter): Boolean {
        if (element.valOrVarKeyword != null) return false
        val constructor = (element.parent as? KtParameterList)?.parent as? KtPrimaryConstructor ?: return false
        if (!constructor.mustHaveValOrVar() && constructor.isExpectDeclaration()) return false
        val containingClass = element.getStrictParentOfType<KtClass>()?:  return false
        return !containingClass.mustHaveOnlyPropertiesInPrimaryConstructor()
    }

    override fun getApplicableRanges(element: KtParameter): List<TextRange> = ApplicabilityRanges.declarationName(element)

    override fun invoke(
        actionContext: ActionContext,
        element: KtParameter,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        addValVarToConstructorParameter(actionContext.project, element, updater)
    }
}
