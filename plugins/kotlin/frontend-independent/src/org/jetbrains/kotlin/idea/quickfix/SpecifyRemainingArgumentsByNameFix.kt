// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingArgumentsData
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtValueArgumentList

sealed class SpecifyRemainingArgumentsByNameFix(
    element: KtValueArgumentList,
    private val remainingArguments: List<Name>,
) : PsiUpdateModCommandAction<KtValueArgumentList>(element) {
    companion object {
        fun createAvailableQuickFixes(
            argumentList: KtValueArgumentList,
            remainingArguments: RemainingArgumentsData
        ): List<SpecifyRemainingArgumentsByNameFix> {
            return buildList {
                add(SpecifyAllRemainingArgumentsByNameFix(argumentList, remainingArguments.allRemainingArguments))
                if (remainingArguments.remainingRequiredArguments.isNotEmpty() &&
                    remainingArguments.remainingRequiredArguments != remainingArguments.allRemainingArguments) {
                    add(SpecifyRemainingRequiredArgumentsByNameFix(argumentList, remainingArguments.remainingRequiredArguments))
                }
            }
        }
    }

    override fun getPresentation(context: ActionContext, element: KtValueArgumentList): Presentation? {
        return super.getPresentation(context, element)?.withPriority(PriorityAction.Priority.HIGH)
    }

    override fun invoke(actionContext: ActionContext, element: KtValueArgumentList, updater: ModPsiUpdater) {
        SpecifyRemainingArgumentsByNameUtil.applyFix(actionContext.project, element, remainingArguments, updater)
    }
}

class SpecifyAllRemainingArgumentsByNameFix(
    element: KtValueArgumentList,
    remainingArguments: List<Name>,
) : SpecifyRemainingArgumentsByNameFix(element, remainingArguments) {
    override fun getFamilyName(): String = KotlinBundle.message("specify.all.remaining.arguments.by.name")
}

class SpecifyRemainingRequiredArgumentsByNameFix(
    element: KtValueArgumentList,
    remainingArguments: List<Name>,
) : SpecifyRemainingArgumentsByNameFix(element, remainingArguments) {
    override fun getFamilyName(): String = KotlinBundle.message("specify.remaining.required.arguments.by.name")
}