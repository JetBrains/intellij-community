// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.types.Variance

class SplitPropertyDeclarationIntention : AbstractKotlinModCommandWithContext<KtProperty, SplitPropertyDeclarationIntention.Context> (
  KtProperty::class
), LowPriorityAction {
    data class Context(val propertyType: String?)

    override fun getFamilyName(): String = KotlinBundle.message("split.property.declaration")

    override fun getActionName(element: KtProperty, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtProperty> = applicabilityRange {
        TextRange(0, it.initializer!!.startOffsetInParent)
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (!element.isLocal || element.parent is KtWhenExpression) return false
        return element.initializer != null
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtProperty): Context? {
        val ktType = element.initializer?.getKtType() ?: return null
        return Context(if (ktType is KtErrorType) null else ktType.render(position = Variance.OUT_VARIANCE))
    }

    override fun apply(element: KtProperty, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        val parent = element.parent

        val initializer = element.initializer ?: return

        val explicitTypeToSet = if (element.typeReference != null) null else context.analyzeContext.propertyType

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