// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.types.Variance

internal class ConvertLateinitPropertyToNullableIntention :
    KotlinApplicableModCommandAction<KtProperty, ConvertLateinitPropertyToNullableIntention.Context>(KtProperty::class) {

    data class Context(
        val nullableType: String,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.nullable.var")

    override fun isApplicableByPsi(element: KtProperty): Boolean =
        element.hasModifier(KtTokens.LATEINIT_KEYWORD)
                && element.isVar
                && element.typeReference?.typeElement !is KtNullableType
                && element.initializer == null

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtProperty): Context {
        val nullableType = element.returnType.withNullability(KaTypeNullability.NULLABLE)
        return Context(nullableType.render(position = Variance.OUT_VARIANCE))
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtProperty,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        element.removeModifier(KtTokens.LATEINIT_KEYWORD)
        element.typeReference = KtPsiFactory(actionContext.project).createType(elementContext.nullableType)
        element.initializer = KtPsiFactory(element.project).createExpression(KtTokens.NULL_KEYWORD.value)
        shortenReferences(element)
    }
}
