// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal object AddEmptyArgumentListFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic) =
        diagnostic.psiElement.parent.safeAs<KtCallExpression>()?.let { AddEmptyArgumentListFix(it) }?.asIntention()
}