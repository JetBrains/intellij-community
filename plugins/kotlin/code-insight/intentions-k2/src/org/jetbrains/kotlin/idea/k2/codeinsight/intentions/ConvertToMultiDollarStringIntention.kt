// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.MultiDollarConversionInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.convertToMultiDollarString
import org.jetbrains.kotlin.idea.codeinsights.impl.base.prepareMultiDollarConversionInfo
import org.jetbrains.kotlin.idea.codeinsights.impl.base.simplifyDollarEntries
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal class ConvertToMultiDollarStringIntention :
    KotlinApplicableModCommandAction<KtStringTemplateExpression, MultiDollarConversionInfo>(KtStringTemplateExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("convert.to.multi.dollar.string")

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean {
        return element.interpolationPrefix == null
                && element.languageVersionSettings.supportsFeature(LanguageFeature.MultiDollarInterpolation)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtStringTemplateExpression,
        elementContext: MultiDollarConversionInfo,
        updater: ModPsiUpdater
    ) {
        val replaced = convertToMultiDollarString(element, elementContext)
        simplifyDollarEntries(replaced)
    }

    context(KaSession)
    override fun prepareContext(element: KtStringTemplateExpression): MultiDollarConversionInfo? {
        return prepareMultiDollarConversionInfo(element, useFallbackPrefix = true)
    }
}
