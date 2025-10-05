// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.types.Variance

internal class ReconstructTypeInCastOrIsIntention :
    KotlinApplicableModCommandAction<KtTypeReference, ReconstructTypeInCastOrIsIntention.Context>(KtTypeReference::class) {

    data class Context(val fqName: String, val shortName: String)

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("replace.by.reconstructed.type")

    override fun getPresentation(
        context: ActionContext,
        element: KtTypeReference,
    ): Presentation? {
        val elementContext = getElementContext(context, element) ?: return null
        val actionName = KotlinBundle.message("replace.by.0", elementContext.shortName)
        return Presentation.of(actionName).withPriority(PriorityAction.Priority.LOW)
    }

    override fun isApplicableByPsi(element: KtTypeReference): Boolean { // Only user types (like Foo) are interesting
        val typeElement = element.typeElement as? KtUserType ?: return false

        // If there are generic arguments already, there's nothing to reconstruct
        if (typeElement.typeArguments.isNotEmpty()) return false

        // We must be on the RHS of as/as?/is/!is or inside an is/!is-condition in when()
        val expression = element.getParentOfType<KtExpression>(true)
        return expression is KtBinaryExpressionWithTypeRHS || element.getParentOfType<KtWhenConditionIsPattern>(true) != null
    }

    @org.jetbrains.kotlin.analysis.api.KaExperimentalApi
    override fun KaSession.prepareContext(element: KtTypeReference): Context? {
        val type = element.type.takeUnless { it is KaErrorType || it.expandedSymbol?.typeParameters?.isEmpty() == true } ?: return null
        return Context(
            fqName = type.render(position = Variance.IN_VARIANCE),
            shortName = type.render(
                renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
                position = Variance.IN_VARIANCE,
            ),
        )
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeReference,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        val newType = KtPsiFactory(actionContext.project).createType(elementContext.shortName)
        shortenReferences(element.replace(newType) as KtTypeReference)
    }
}
