// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.refactoring.moveFunctionLiteralOutsideParenthesesIfPossible
import org.jetbrains.kotlin.idea.k2.refactoring.util.AnonymousFunctionToLambdaUtil
import org.jetbrains.kotlin.idea.refactoring.getLastLambdaExpression
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.contentRange
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

class AnonymousFunctionToLambdaIntention : KotlinApplicableModCommandAction<KtNamedFunction, KtExpression>(KtNamedFunction::class) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.anonymous.function.to.lambda.expression")

    override fun getApplicableRanges(element: KtNamedFunction): List<TextRange> {
        if (element.name != null || !element.hasBody()) return emptyList()
        return ApplicabilityRange.single(element) { it.funKeyword }
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtNamedFunction,
        elementContext: KtExpression,
        updater: ModPsiUpdater
    ) {
        AnonymousFunctionToLambdaUtil.convertAnonymousFunctionToLambda(element, elementContext)
    }

    context(KaSession)
    override fun prepareContext(element: KtNamedFunction): KtExpression? = AnonymousFunctionToLambdaUtil.prepareAnonymousFunctionToLambdaContext(element)
}