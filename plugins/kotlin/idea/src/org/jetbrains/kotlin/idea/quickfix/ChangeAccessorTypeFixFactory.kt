// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object ChangeAccessorTypeFixFactory : KotlinSingleIntentionActionFactory() {
    public override fun createAction(diagnostic: Diagnostic): ChangeAccessorTypeFix? {
        return diagnostic.psiElement.getNonStrictParentOfType<KtPropertyAccessor>()?.let(::ChangeAccessorTypeFix)
    }
}
