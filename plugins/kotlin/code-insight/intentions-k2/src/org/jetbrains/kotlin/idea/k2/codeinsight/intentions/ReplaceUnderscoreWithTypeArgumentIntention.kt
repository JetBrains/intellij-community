// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.UnderscoreTypeArgumentsUtils.isUnderscoreTypeArgument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.UnderscoreTypeArgumentsUtils.replaceTypeProjection
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.types.Variance

internal class ReplaceUnderscoreWithTypeArgumentIntention :
    KotlinApplicableModCommandAction<KtTypeProjection, ReplaceUnderscoreWithTypeArgumentIntention.Context>(KtTypeProjection::class) {

    data class Context(
        var updatedTypeProjection: SmartPsiElementPointer<KtTypeProjection>,
    )

    override fun getFamilyName(): String = KotlinBundle.message("replace.with.explicit.type")

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtTypeProjection): Context? {
        val newType = element.resolveType() ?: return null
        if (newType is KaErrorType) return null

        // Any of Variance is fine for this case
        val renderedNewType = newType.render(position = Variance.OUT_VARIANCE)

        val argumentList = element.parent as? KtTypeArgumentList ?: return null
        val newTypeProjection = replaceTypeProjection(element, argumentList, renderedNewType)
        return Context(newTypeProjection.createSmartPointer())
    }

    override fun isApplicableByPsi(element: KtTypeProjection): Boolean =
        isUnderscoreTypeArgument(element)

    context(_: KaSession)
    private fun KtTypeProjection.resolveType(): KaType? {
        val typeArgumentList = parent as KtTypeArgumentList
        val call = (typeArgumentList.parent as? KtCallExpression)?.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val argumentsTypes = call.typeArgumentsMapping.map { it.value }.toTypedArray()
        val resolvedElementIndex = typeArgumentList.arguments.indexOf(this)
        return if (resolvedElementIndex < argumentsTypes.size) argumentsTypes[resolvedElementIndex] else null
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtTypeProjection,
      elementContext: Context,
      updater: ModPsiUpdater
    ) {
        val updatedTypeProjection = elementContext.updatedTypeProjection.dereference() ?: return
        val replacedElement = element.replace(updatedTypeProjection) as? KtElement ?: return
        shortenReferences(replacedElement)
    }
}