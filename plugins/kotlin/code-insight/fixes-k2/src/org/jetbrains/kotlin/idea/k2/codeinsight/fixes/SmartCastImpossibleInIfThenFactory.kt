// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeInsight.IfThenToElviFix
import org.jetbrains.kotlin.idea.codeInsight.IfThenToSafeAccessFix
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationUtils
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object SmartCastImpossibleInIfThenFactory  {
    val smartcastImpossible = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SmartcastImpossible ->
        val element = diagnostic.psi as? KtNameReferenceExpression ?: return@ModCommandBased emptyList()
        val ifExpression =
            element.getStrictParentOfType<KtContainerNodeForControlStructureBody>()?.parent as? KtIfExpression
                ?: return@ModCommandBased emptyList()

        return@ModCommandBased listOfNotNull(
            IfThenTransformationUtils.prepareIfThenTransformationStrategy(ifExpression, true)?.let {
                IfThenToSafeAccessFix(it).asModCommandAction(ifExpression)
            },
            IfThenTransformationUtils.prepareIfThenToElvisInspectionData(ifExpression)?.let {
                IfThenToElviFix(it).asModCommandAction(ifExpression)
            }
        )
    }

}
