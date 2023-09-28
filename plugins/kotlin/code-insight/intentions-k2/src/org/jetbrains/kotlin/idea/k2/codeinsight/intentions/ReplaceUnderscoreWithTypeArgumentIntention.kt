// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.UnderscoreTypeArgumentsUtils.isUnderscoreTypeArgument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.UnderscoreTypeArgumentsUtils.replaceTypeProjection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.types.Variance

internal class ReplaceUnderscoreWithTypeArgumentIntention :
    AbstractKotlinModCommandWithContext<KtTypeProjection, ReplaceUnderscoreWithTypeArgumentIntention.Context>(KtTypeProjection::class) {
    class Context(var updatedTypeProjection: SmartPsiElementPointer<KtTypeProjection>)

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.explicit.type")

    override fun getActionName(element: KtTypeProjection, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtTypeProjection> = ApplicabilityRanges.SELF

    context(KtAnalysisSession)
    override fun prepareContext(element: KtTypeProjection): Context? {
        val newType = element.resolveType() ?: return null
        if (newType is KtTypeErrorType) return null

        // Any of Variance is fine for this case
        val renderedNewType = newType.render(position = Variance.OUT_VARIANCE)

        val argumentList = element.parent as? KtTypeArgumentList ?: return null
        val newTypeProjection = replaceTypeProjection(element, argumentList, renderedNewType)
        return Context(newTypeProjection.createSmartPointer())
    }

    override fun apply(element: KtTypeProjection, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        val updatedTypeProjection = context.analyzeContext.updatedTypeProjection.dereference() ?: return
        val replacedElement = element.replace(updatedTypeProjection) as? KtElement ?: return
        shortenReferences(replacedElement)
    }

    override fun isApplicableByPsi(element: KtTypeProjection): Boolean =
        isUnderscoreTypeArgument(element)

    context(KtAnalysisSession)
    private fun KtTypeProjection.resolveType(): KtType? {
        val typeArgumentList = parent as KtTypeArgumentList
        val call = (typeArgumentList.parent as? KtCallExpression)?.resolveCall()?.singleFunctionCallOrNull() ?: return null
        val argumentsTypes = call.typeArgumentsMapping.map { it.value }.toTypedArray()
        val resolvedElementIndex = typeArgumentList.arguments.indexOf(this)
        return argumentsTypes[resolvedElementIndex]
    }
}