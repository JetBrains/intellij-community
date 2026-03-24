// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtContextReceiver
import org.jetbrains.kotlin.psi.KtPsiFactory

internal object ContextParameterWithoutNameFixFactory {
    val addUnderscoreToContextParameter = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ContextParameterWithoutName ->
        listOf(AddUnderscoreToContextParameterFix(diagnostic.psi))
    }

    class AddUnderscoreToContextParameterFix(element: KtContextReceiver) : PsiUpdateModCommandAction<KtContextReceiver>(element) {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("add.underscore.as.context.parameter.name")

        override fun invoke(
            context: ActionContext,
            element: KtContextReceiver,
            updater: ModPsiUpdater,
        ) {
            val anonymousParameter = KtPsiFactory(context.project).createParameter("_: ${element.text}")
            element.replace(anonymousParameter)
        }
    }
}
