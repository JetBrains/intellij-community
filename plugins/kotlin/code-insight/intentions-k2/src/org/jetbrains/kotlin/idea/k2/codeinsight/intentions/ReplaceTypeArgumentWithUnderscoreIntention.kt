// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.isAnnotatedDeep
import org.jetbrains.kotlin.idea.k2.refactoring.util.canBeReplacedWithUnderscore
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection

internal class ReplaceTypeArgumentWithUnderscoreIntention : KotlinApplicableModCommandAction<KtTypeProjection, Unit>(
    KtTypeProjection::class
) {
    override fun invoke(
        actionContext: ActionContext,
        element: KtTypeProjection,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        replaceWithUnderscore(element)
    }

    override fun isApplicableByPsi(element: KtTypeProjection): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.PartiallySpecifiedTypeArguments)) return false
        if (element.typeReference?.isPlaceholder == true) return false
        val typeArgumentList = element.parent as? KtTypeArgumentList ?: return false
        return !typeArgumentList.arguments.any { it.typeReference?.isAnnotatedDeep() == true }
    }

    override fun KaSession.prepareContext(element: KtTypeProjection): Unit? {
        val typeArgumentList = element.parent as? KtTypeArgumentList ?: return null
        val callExpression = typeArgumentList.parent as? KtCallExpression ?: return null
        if (!element.canBeReplacedWithUnderscore(callExpression)) return null
        return Unit
    }

    private fun replaceWithUnderscore(element: KtTypeProjection) {
        val newTypeProjection = KtPsiFactory(element.project).createTypeArgument("_")
        element.replace(newTypeProjection)
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.underscore")
}