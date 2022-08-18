// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import org.jetbrains.kotlin.idea.KotlinIconProvider
import org.jetbrains.kotlin.idea.util.hasMatchingExpected
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

class Fe10KotlinIconProvider : KotlinIconProvider() {
    override fun isMatchingExpected(declaration: KtDeclaration): Boolean {
        return declaration.hasActualModifier() && declaration.hasMatchingExpected()
    }
}