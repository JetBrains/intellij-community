// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.refactoring.classForRefactor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object MakeClassAnAnnotationClassFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val typeReference = diagnostic.psiElement.getNonStrictParentOfType<KtAnnotationEntry>()?.typeReference ?: return null
        val klass = typeReference.classForRefactor() ?: return null
        return MakeClassAnAnnotationClassFix(klass).asIntention()
    }
}