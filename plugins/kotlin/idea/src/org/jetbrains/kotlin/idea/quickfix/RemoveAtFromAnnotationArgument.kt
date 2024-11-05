// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.util.firstLeaf
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.psi.KtAnnotationEntry

class RemoveAtFromAnnotationArgument(
    element: KtAnnotationEntry,
) : PsiUpdateModCommandAction<KtAnnotationEntry>(element) {

    override fun getFamilyName(): String = KotlinBundle.message("remove.from.annotation.argument")

    override fun invoke(
        context: ActionContext,
        element: KtAnnotationEntry,
        updater: ModPsiUpdater,
    ) {
        val firstLeaf = element.firstLeaf()
        assert(firstLeaf.text == "@") {
            "Expected '@' at the beginning of the annotation argument, but found '${firstLeaf.text}'"
        }
        firstLeaf.delete()
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? =
            RemoveAtFromAnnotationArgument(diagnostic.psiElement as KtAnnotationEntry).asIntention()
    }
}
