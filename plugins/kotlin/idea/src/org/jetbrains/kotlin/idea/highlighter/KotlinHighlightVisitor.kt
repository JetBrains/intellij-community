// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
import org.jetbrains.kotlin.idea.isMainFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.util.match

open class KotlinHighlightVisitor: AbstractKotlinHighlightVisitor() {

    override fun shouldSuppressUnusedParameter(parameter: KtParameter): Boolean {
        val grandParent = parameter.parents.match(KtParameterList::class, last = KtNamedFunction::class) ?: return false
        if (!UnusedSymbolInspection.isEntryPoint(grandParent)) return false
        return !grandParent.isMainFunction()
    }

    override fun clone(): HighlightVisitor = KotlinHighlightVisitor()
}