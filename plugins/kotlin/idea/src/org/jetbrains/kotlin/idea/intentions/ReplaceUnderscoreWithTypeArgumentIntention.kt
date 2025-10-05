// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsights.impl.base.UnderscoreTypeArgumentsUtils.isUnderscoreTypeArgument
import org.jetbrains.kotlin.idea.codeinsights.impl.base.UnderscoreTypeArgumentsUtils.replaceTypeProjection
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.types.error.ErrorType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isNullableAny

class ReplaceUnderscoreWithTypeArgumentIntention : SelfTargetingRangeIntention<KtTypeProjection>(
    KtTypeProjection::class.java,
    KotlinBundle.messagePointer("replace.with.explicit.type")
), LowPriorityAction {

    override fun applicabilityRange(element: KtTypeProjection): TextRange? {
        if (!isUnderscoreTypeArgument(element)) return null
        val resolvedType = element.resolveType() ?: return null
        if (resolvedType.isNullableAny() || resolvedType is ErrorType) return null
        return element.textRange
    }

    override fun applyTo(element: KtTypeProjection, editor: Editor?) {
        val argumentList = element.parent as? KtTypeArgumentList ?: return
        val resolvedType = element.resolveType() ?: return
        val renderedNewType = IdeDescriptorRenderers.SOURCE_CODE.renderType(resolvedType)
        val newTypeProjection = replaceTypeProjection(element, argumentList, renderedNewType)
        val replacedElement = element.replace(newTypeProjection) as? KtElement ?: return
        ShortenReferences.DEFAULT.process(replacedElement)
    }

    private fun KtTypeProjection.resolveType(): KotlinType? {
        val callExpression = ((parent as? KtTypeArgumentList)?.parent as? KtCallExpression) ?: return null
        val indexOfReplacedType = callExpression.typeArguments.indexOf(this)
        val resolveCall = callExpression.resolveToCall(BodyResolveMode.PARTIAL) ?: return null
        return resolveCall.typeArguments.map { it.value }.getOrNull(indexOfReplacedType)
    }

}