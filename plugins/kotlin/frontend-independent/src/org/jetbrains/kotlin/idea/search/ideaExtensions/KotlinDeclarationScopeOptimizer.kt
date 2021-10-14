// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.openapi.roots.ProjectFileIndex
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

        val fileScope = containingFile.fileScope()

        val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(element.project)
        // it is possible to create new kotlin private class from java - so have to look up in the same module as well
        val moduleScope = projectFileIndex.getModuleForFile(containingFile.virtualFile)?.moduleScope?.excludeKotlinSources()
        return moduleScope?.let { fileScope.union(moduleScope) } ?: fileScope
    }
}