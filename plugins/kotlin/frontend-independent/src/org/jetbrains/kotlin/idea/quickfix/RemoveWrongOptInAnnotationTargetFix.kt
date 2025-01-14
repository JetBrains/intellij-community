// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinPsiOnlyQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.PsiElementSuitabilityCheckers
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixesPsiBasedFactory
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.checkers.OptInDescription

class RemoveWrongOptInAnnotationTargetFix(annotationEntry: KtAnnotationEntry) :
    KotlinPsiOnlyQuickFixAction<KtAnnotationEntry>(annotationEntry) {

    override fun getText(): String = KotlinBundle.message("fix.opt_in.remove.all.forbidden.targets")

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val annotationEntry = element ?: return
        val argumentList = annotationEntry.valueArgumentList ?: return
        val forbiddenArguments: List<KtValueArgument> = argumentList.arguments.filter {
            val text = it.text ?: return@filter false
            WRONG_TARGETS.any { name -> text.endsWith(name) }
        }

        if (forbiddenArguments.size == argumentList.arguments.size) {
            annotationEntry.delete()
        } else {
            forbiddenArguments.forEach {
                argumentList.removeArgument(it)
            }
        }
    }

    companion object : QuickFixesPsiBasedFactory<KtAnnotationEntry>(KtAnnotationEntry::class, PsiElementSuitabilityCheckers.ALWAYS_SUITABLE) {
        override fun doCreateQuickFix(psiElement: KtAnnotationEntry): List<IntentionAction> =
            listOf(RemoveWrongOptInAnnotationTargetFix(psiElement))

        private val WRONG_TARGETS: List<String> = OptInDescription.WRONG_TARGETS_FOR_MARKER.map { it.toString() }
    }
}