// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expectedType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.builtins.StandardNames.KOTLIN_REFLECT_FQ_NAME
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.k2.refactoring.util.ConvertReferenceToLambdaUtil
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression

internal class ConvertReferenceToLambdaIntention :
    KotlinApplicableModCommandAction<KtCallableReferenceExpression, String>(KtCallableReferenceExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.reference.to.lambda")

    override fun KaSession.prepareContext(element: KtCallableReferenceExpression): String? =
        if (skip(element)) null
        else ConvertReferenceToLambdaUtil.prepareLambdaExpressionText(element)

    context(_: KaSession)
    private fun skip(element: KtCallableReferenceExpression): Boolean {
        val expectedType = element.expectedType ?: return false
        val classId = (expectedType as? KaClassType)?.classId ?: return false
        val packageFqName = classId.packageFqName
        return !packageFqName.isRoot && packageFqName == KOTLIN_REFLECT_FQ_NAME
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallableReferenceExpression,
      elementContext: String,
      updater: ModPsiUpdater,
    ) {
        ConvertReferenceToLambdaUtil.convertReferenceToLambdaExpression(element, elementContext)
    }
}