// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken

fun interface PsiElementSuitabilityChecker<in PSI: PsiElement> {
    fun isSupported(psiElement: PSI): Boolean
}

object PsiElementSuitabilityCheckers {
    val ALWAYS_SUITABLE = PsiElementSuitabilityChecker<PsiElement> { true }

    val MODIFIER = PsiElementSuitabilityChecker<LeafPsiElement> { psiElement ->
        psiElement.elementType is KtModifierKeywordToken
    }
}
