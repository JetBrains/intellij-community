// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtValueArgumentList

sealed class SpecifyRemainingArgumentsByNameFix(
    element: KtValueArgumentList,
    private val remainingValueArguments: List<Name>,
    private val remainingContextArguments: List<Name> = emptyList(),
    private val allContextParameterNames: Set<Name> = emptySet(),
) : KotlinPsiUpdateModCommandAction.ElementContextless<KtValueArgumentList>(element) {
    companion object {
        fun createAvailableQuickFixes(
            argumentList: KtValueArgumentList,
            remainingArguments: RemainingArgumentsData
        ): List<SpecifyRemainingArgumentsByNameFix> {
            return buildList {
                val remainingValueArguments = remainingArguments.allValueRemainingArguments
                val remainingRequiredArguments = remainingArguments.remainingRequiredArguments

                if (
                    remainingValueArguments.isNotEmpty() ||
                    argumentList.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)
                ) {
                    add(
                        SpecifyAllRemainingArgumentsByNameFix(
                            argumentList,
                            remainingValueArguments,
                            remainingArguments.allContextRemainingArguments,
                            remainingArguments.allContextParameterNames
                        )
                    )
                }

                if (remainingRequiredArguments.isNotEmpty() &&
                    remainingRequiredArguments != remainingValueArguments
                ) {
                    add(SpecifyRemainingRequiredArgumentsByNameFix(argumentList, remainingRequiredArguments))
                }
            }
        }
    }

    override fun getActionPresentation(context: ActionContext, element: KtValueArgumentList): Presentation? {
        return super.getActionPresentation(context, element)?.withPriority(PriorityAction.Priority.HIGH)
    }

    override fun invoke(context: ActionContext, element: KtValueArgumentList, updater: ModPsiUpdater) {
        SpecifyRemainingArgumentsByNameUtil.applyFix(
            context.project,
            element,
            remainingValueArguments,
            remainingContextArguments,
            allContextParameterNames,
            updater
        )
    }
}

class SpecifyAllRemainingArgumentsByNameFix(
    element: KtValueArgumentList,
    remainingValueArguments: List<Name>,
    remainingContextArguments: List<Name>,
    allContextParameterNames: Set<Name>
) : SpecifyRemainingArgumentsByNameFix(element, remainingValueArguments, remainingContextArguments, allContextParameterNames) {
    override fun getFamilyName(): String = KotlinBundle.message("specify.all.remaining.arguments.by.name")
}

class SpecifyRemainingRequiredArgumentsByNameFix(
    element: KtValueArgumentList,
    remainingArguments: List<Name>,
) : SpecifyRemainingArgumentsByNameFix(element, remainingArguments) {
    override fun getFamilyName(): String = KotlinBundle.message("specify.remaining.required.arguments.by.name")
}