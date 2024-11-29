// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.types.Variance

internal class SplitPropertyDeclarationIntention :
    KotlinApplicableModCommandAction<KtProperty, SplitPropertyDeclarationIntention.Context>(KtProperty::class) {

    data class Context(
        val propertyType: String?,
    )

    override fun getFamilyName(): String = KotlinBundle.message("split.property.declaration")
    override fun getPresentation(context: ActionContext, element: KtProperty): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun getApplicableRanges(element: KtProperty): List<TextRange> =
        listOf(TextRange(0, element.initializer!!.startOffsetInParent))

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (!element.isLocal || element.parent is KtWhenExpression) return false
        return element.initializer != null
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun prepareContext(element: KtProperty): Context? {
        val ktType = element.initializer?.expressionType ?: return null
        return Context(if (ktType is KaErrorType) null else ktType.render(position = Variance.OUT_VARIANCE))
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtProperty,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        val parent = element.parent

        val initializer = element.initializer ?: return

        val explicitTypeToSet = if (element.typeReference != null) null else elementContext.propertyType

        val psiFactory = KtPsiFactory(element.project)

        parent.addAfter(psiFactory.createExpressionByPattern("$0 = $1", element.nameAsName!!, initializer), element)
        parent.addAfter(psiFactory.createNewLine(), element)

        element.initializer = null

        if (explicitTypeToSet != null) {
            val typeReference = KtPsiFactory(element.project).createType(explicitTypeToSet)
            element.setTypeReference(typeReference)?.let { shortenReferences(it) }
        }
    }
}