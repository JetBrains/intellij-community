// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.mustHaveOnlyValPropertiesInPrimaryConstructor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ValVarExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddValVarToConstructorParameterFixFactory {

    val dataClassNotPropertyParameterFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.DataClassNotPropertyParameter ->
        listOf(
            AddValVarToConstructorParameterFix(diagnostic.psi)
        )
    }

    val missingValOnAnnotationParameterFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.MissingValOnAnnotationParameter ->
        listOf(
            AddValVarToConstructorParameterFix(diagnostic.psi)
        )
    }

    val valueClassConstructorNotFinalReadOnlyParameterFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValueClassConstructorNotFinalReadOnlyParameter ->
        val parameter = diagnostic.psi
        if (parameter.isMutable) return@ModCommandBased emptyList()

        listOf(
            AddValVarToConstructorParameterFix(parameter)
        )
    }

    private class AddValVarToConstructorParameterFix(element: KtParameter) : PsiUpdateModCommandAction<KtParameter>(element) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtParameter,
            updater: ModPsiUpdater,
        ) {
            val valKeyword = element.addBefore(KtPsiFactory(actionContext.project).createValKeyword(), element.nameIdentifier)
            if (element.containingClass()?.mustHaveOnlyValPropertiesInPrimaryConstructor() == true) return
            updater.templateBuilder().field(valKeyword, ValVarExpression)
        }

        override fun getPresentation(
            context: ActionContext,
            element: KtParameter,
        ): Presentation {
            val key = if (element.getStrictParentOfType<KtClass>()?.mustHaveOnlyValPropertiesInPrimaryConstructor() == true) {
                "add.val.to.parameter.0"
            } else {
                "add.val.var.to.parameter.0"
            }
            val actionName = KotlinBundle.message(key, element.name ?: "")
            return Presentation.of(actionName)
        }

        override fun getFamilyName(): String = KotlinBundle.message("add.val.var.to.primary.constructor.parameter")
    }
}
