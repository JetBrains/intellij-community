// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.SpecifyRemainingArgumentsByNameUtil.RemainingNamedArgumentData
import org.jetbrains.kotlin.psi.KtValueArgumentList

sealed class SpecifyRemainingArgumentsByNameFix(
    element: KtValueArgumentList,
    private val remainingArguments: List<RemainingNamedArgumentData>
) : PsiUpdateModCommandAction<KtValueArgumentList>(element) {
    companion object {
        fun createAvailableQuickFixes(
            argumentList: KtValueArgumentList,
            remainingArguments: List<RemainingNamedArgumentData>
        ): List<SpecifyRemainingArgumentsByNameFix> {
            val argumentsWithDefault = remainingArguments.count { it.hasDefault }

            return buildList {
                add(SpecifyAllRemainingArgumentsByNameFix(argumentList, remainingArguments))
                if (argumentsWithDefault in (1..<remainingArguments.size)) {
                    add(SpecifyRemainingRequiredArgumentsByNameFix(argumentList, remainingArguments.filter { !it.hasDefault }))
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
    remainingArguments: List<RemainingNamedArgumentData>
) : SpecifyRemainingArgumentsByNameFix(element, remainingArguments) {
    override fun getFamilyName(): String = KotlinBundle.getMessage("specify.all.remaining.arguments.by.name")
}

class SpecifyRemainingRequiredArgumentsByNameFix(
    element: KtValueArgumentList,
    remainingArguments: List<RemainingNamedArgumentData>
) : SpecifyRemainingArgumentsByNameFix(element, remainingArguments) {
    override fun getFamilyName(): String = KotlinBundle.getMessage("specify.remaining.required.arguments.by.name")
}