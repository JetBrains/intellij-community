// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object MoveReceiverAnnotationFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val entry = diagnostic.psiElement as? KtAnnotationEntry ?: return null

        if (entry.useSiteTarget?.getAnnotationUseSiteTarget() != AnnotationUseSiteTarget.RECEIVER) return null

        val declaration = entry.getParentOfType<KtCallableDeclaration>(true) ?: return null
        if (declaration.receiverTypeReference == null) return null

        return MoveReceiverAnnotationFix(entry).asIntention()
    }
}