// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtModifierListOwner

internal object AddModifierFixMppFactory : AddModifierFix.Factory<ModCommandAction> {
    override fun createModifierFix(element: KtModifierListOwner, modifier: KtModifierKeywordToken): ModCommandAction =
        AddModifierFixMpp(element, modifier)
}
