// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.OPT_IN_MARKER_WITH_WRONG_TARGET
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.checkers.OptInDescription
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

object RemoveWrongOptInAnnotationTargetFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        if (diagnostic.factory != OPT_IN_MARKER_WITH_WRONG_TARGET) return emptyList()
        val annotationEntry = diagnostic.psiElement.safeAs<KtAnnotationEntry>() ?: return emptyList()
        return listOf(RemoveAllForbiddenOptInTargetsFix(annotationEntry))
    }

    private class RemoveAllForbiddenOptInTargetsFix(annotationEntry: KtAnnotationEntry) :
      KotlinQuickFixAction<KtAnnotationEntry>(annotationEntry) {

        override fun getText(): String {
            return KotlinBundle.message("fix.opt_in.remove.all.forbidden.targets")
        }

        override fun getFamilyName(): String = text

        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val annotationEntry = element ?: return
            val argumentList = annotationEntry.valueArgumentList ?: return
            val forbiddenArguments: List<KtValueArgument> = argumentList.arguments.filter {
                WRONG_TARGETS.any { name -> it.text?.contains(name) == true }
            }

            if (forbiddenArguments.size == argumentList.arguments.size) {
                annotationEntry.delete()
            } else {
                forbiddenArguments.forEach {
                    argumentList.removeArgument(it)
                }
            }
        }

        companion object {
            private val WRONG_TARGETS: List<String> = OptInDescription.WRONG_TARGETS_FOR_MARKER.map {it.toString() }
        }
    }
}
