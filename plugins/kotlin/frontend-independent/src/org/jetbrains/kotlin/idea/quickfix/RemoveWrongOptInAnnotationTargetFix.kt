// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.checkers.OptInDescription

class RemoveWrongOptInAnnotationTargetFix(element: KtAnnotationEntry) :
    PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.opt_in.remove.all.forbidden.targets")

    override fun invoke(context: ActionContext, element: KtAnnotationEntry, updater: ModPsiUpdater) {
        val argumentList = element.valueArgumentList ?: return
        val forbiddenArguments: List<KtValueArgument> = argumentList.arguments.filter {
            val text = it.text ?: return@filter false
            WRONG_TARGETS.any { name -> text.endsWith(name) }
        }

        if (forbiddenArguments.size == argumentList.arguments.size) {
            element.delete()
        } else {
            forbiddenArguments.forEach {
                argumentList.removeArgument(it)
            }
        }
    }

    companion object : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(
                RemoveWrongOptInAnnotationTargetFix(psiElement).asIntention()
            )

        private val WRONG_TARGETS: List<String> = OptInDescription.WRONG_TARGETS_FOR_MARKER.map { it.toString() }
    }
}