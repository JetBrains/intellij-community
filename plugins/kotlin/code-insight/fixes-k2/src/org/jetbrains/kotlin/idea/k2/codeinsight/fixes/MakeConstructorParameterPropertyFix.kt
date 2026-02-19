// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.MakeConstructorParameterPropertyFix
import org.jetbrains.kotlin.idea.quickfix.getPrimaryConstructorParameterWithSameName
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinValVar
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS

internal object UnresolvedReferenceFixFactories {

    val makeConstructorParameterPropertyFix = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        val element = diagnostic.psi as? KtNameReferenceExpression ?: return@ModCommandBased emptyList()

        listOfNotNull(
            createFixIfAvailable(element)
        )
    }

    private fun createFixIfAvailable(
        element: KtNameReferenceExpression,
    ): MakeConstructorParameterPropertyFix? {
        val valOrVar = if (element.getAssignmentByLHS() != null) KotlinValVar.Var else KotlinValVar.Val
        val parameter = element.getPrimaryConstructorParameterWithSameName() ?: return null
        if (parameter.hasValOrVar()) return null
        val containingClass = parameter.containingClass() ?: return null
        val className = if (containingClass != element.containingClass()) containingClass.nameAsSafeName.asString() else null
        return MakeConstructorParameterPropertyFix(parameter, valOrVar, className)
    }
}
