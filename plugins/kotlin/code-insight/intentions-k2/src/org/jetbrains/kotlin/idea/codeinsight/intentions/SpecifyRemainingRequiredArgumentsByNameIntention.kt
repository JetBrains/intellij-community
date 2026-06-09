// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.psi.KtElement

internal class SpecifyRemainingRequiredArgumentsByNameIntention : SpecifyRemainingArgumentsByNameIntention() {

    override fun getFamilyName(): String = KotlinBundle.message("specify.remaining.required.arguments.by.name")

    override fun shouldShowFor(element: KtElement, remainingArgumentsData: RemainingArgumentsData): Boolean {
        // We return false in case `SpecifyAllRemainingArgumentsByNameIntention` would result in the same change.
        return remainingArgumentsData.remainingRequiredArguments.isNotEmpty() &&
                remainingArgumentsData.remainingRequiredArguments != remainingArgumentsData.allValueRemainingArguments
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
            remainingValueArguments = elementContext.remainingRequiredArguments,
            remainingContextArguments = elementContext.allContextRemainingArguments,
            allContextParameterNames = elementContext.allContextParameterNames,
            updater = updater
        )
    }
}
