// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesHandlerFactory

internal class K1MoveRefactoringSupport : KotlinMoveRefactoringSupport {
    override fun findReferencesToHighlight(target: PsiElement, searchScope: SearchScope): Collection<PsiReference> {
        return KotlinFindUsagesHandlerFactory(target.project).createFindUsagesHandler(target, false)
            .findReferencesToHighlight(target, searchScope)
    }
}