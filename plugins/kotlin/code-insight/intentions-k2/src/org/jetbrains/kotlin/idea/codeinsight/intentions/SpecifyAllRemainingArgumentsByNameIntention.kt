// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.psi.KtElement

internal class SpecifyAllRemainingArgumentsByNameIntention : SpecifyRemainingArgumentsByNameIntention() {

    override fun getFamilyName(): String = KotlinBundle.message("specify.all.remaining.arguments.by.name")

    override fun shouldShowFor(element: KtElement, remainingArgumentsData: RemainingArgumentsData): Boolean {
        return remainingArgumentsData.remainingRequiredArguments.isNotEmpty() ||
                element.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtElement,
        elementContext: RemainingArgumentsData,
        updater: ModPsiUpdater
    ) {
        val argumentList = element.getValueArgumentList() ?: return
        SpecifyRemainingArgumentsByNameUtil.applyFix(
            project = actionContext.project,
            element = argumentList,
            remainingValueArguments = elementContext.allValueRemainingArguments,
            remainingContextArguments = elementContext.allContextRemainingArguments,
            allContextParameterNames = elementContext.allContextParameterNames,
            updater = updater
        )
    }
}
