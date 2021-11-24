// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.ScopeOptimizer
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.search.excludeKotlinSources
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinDeclarationScopeOptimizer : ScopeOptimizer {
    override fun getRestrictedUseScope(element: PsiElement): SearchScope? {
        val declaration = element.unwrapped?.safeAs<KtDeclaration>() ?: return null
        val isPrivateDeclaration = declaration.isPrivate() ||
                element.safeAs<PsiModifierListOwner>()?.hasModifier(JvmModifier.PRIVATE) == true

        val privateClass = declaration.parentsOfType<KtClassOrObject>(withSelf = true).find(KtClassOrObject::isPrivate)
        if (privateClass == null && !isPrivateDeclaration) return null

        val containingFile = declaration.containingKtFile
        val fileScope = containingFile.fileScope()
        if (declaration !is KtClassOrObject && isPrivateDeclaration || privateClass?.isTopLevel() != true) return fileScope

        // it is possible to create new kotlin private class from java - so have to look up in the same module as well
        val jvmScope = findJvmScope(containingFile, fallback = { element.useScope })

        return fileScope.union(jvmScope.excludeKotlinSources())
    }
}

private fun findJvmScope(file: KtFile, fallback: () -> SearchScope): SearchScope {
    val project = file.project
    val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)

    val virtualFile = file.virtualFile ?: return fallback()
    val moduleScope = projectFileIndex.getModuleForFile(virtualFile)?.moduleScope
    return when {
        moduleScope != null -> moduleScope
        projectFileIndex.isInLibrary(virtualFile) -> ProjectScope.getLibrariesScope(project)
        else -> fallback()
    }
}