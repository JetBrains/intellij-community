// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.predicates

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import org.jetbrains.kotlin.psi.KtProperty

class KotlinVarValOnlyPredicate(private val isVar: Boolean) : MatchPredicate() {
    override fun match(matchedNode: PsiElement, start: Int, end: Int, context: MatchContext): Boolean {
        val parent = matchedNode.parent
        if (parent !is KtProperty) return false
        return parent.isVar == isVar
    }
}