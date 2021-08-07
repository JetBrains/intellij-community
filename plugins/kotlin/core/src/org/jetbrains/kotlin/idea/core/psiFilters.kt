// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.core

import com.intellij.psi.filters.position.PositionElementFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class FirstChildInParentFilter(val level: Int = 1) : PositionElementFilter() {
    override fun isAcceptable(element: Any?, context: PsiElement?): Boolean {
        if (element !is PsiElement) return false

        var parent: PsiElement? = element
        for (i in 1..level) {
            if (parent == null) break
            parent = parent.context
        }

        return (parent != null) && PsiTreeUtil.isAncestor(parent.firstChild, element, true)
    }


    override fun toString(): String {
        return "firstChildInParent($level)"
    }
}

