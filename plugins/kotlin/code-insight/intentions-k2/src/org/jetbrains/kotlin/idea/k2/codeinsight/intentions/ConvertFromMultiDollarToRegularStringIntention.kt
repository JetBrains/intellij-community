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
import org.jetbrains.kotlin.idea.codeinsights.impl.base.convertToStringWithoutPrefix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.simplifyDollarEntries
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ConvertFromMultiDollarToRegularStringIntention :
    KotlinApplicableModCommandAction<KtStringTemplateExpression, Unit>(KtStringTemplateExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.string.without.prefix")

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.MultiDollarInterpolation)) return false
        if (element.interpolationPrefix == null) return false
        return true
    }

    override fun KaSession.prepareContext(element: KtStringTemplateExpression) {
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtStringTemplateExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val stringWithoutPrefix = convertToStringWithoutPrefix(element)
        simplifyDollarEntries(stringWithoutPrefix)
    }
}
