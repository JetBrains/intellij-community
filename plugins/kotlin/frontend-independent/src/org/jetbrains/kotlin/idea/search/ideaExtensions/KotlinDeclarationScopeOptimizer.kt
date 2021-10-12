// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiElement
import com.intellij.psi.search.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.search.excludeKotlinSources
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinDeclarationScopeOptimizer: ScopeOptimizer {
    override fun getRestrictedUseScope(element: PsiElement): SearchScope? {
        val declaration = element.safeAs<KtDeclaration>() ?: return null
        val ktClassOrObject =
            PsiTreeUtil.getParentOfType(declaration, KtClassOrObject::class.java)?.takeIf { it.isPrivate() } ?: return null
        val containingFile = ktClassOrObject.containingKtFile
        val packageScope = GlobalSearchScopesCore.directoryScope(element.project, containingFile.virtualFile.parent, false)
            .excludeKotlinSources()

        return containingFile.fileScope().union(packageScope)
    }
}