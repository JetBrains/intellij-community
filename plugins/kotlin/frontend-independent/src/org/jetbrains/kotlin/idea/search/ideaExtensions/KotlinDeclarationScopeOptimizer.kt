// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.search.*
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.search.excludeKotlinSources
import org.jetbrains.kotlin.idea.search.fileScope
import org.jetbrains.kotlin.idea.util.psiPackage
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
        val minimalScope = findModuleOrLibraryScope(containingFile) ?: element.useScope
        val scopeWithoutKotlin = minimalScope.excludeKotlinSources(containingFile.project)
        val psiPackage = containingFile.psiPackage
        val jvmScope = if (psiPackage != null && scopeWithoutKotlin is GlobalSearchScope)
            PackageScope.packageScope(psiPackage, /* includeSubpackages = */ false, scopeWithoutKotlin)
        else
            scopeWithoutKotlin

        return fileScope.union(jvmScope)
    }
}

private fun findModuleOrLibraryScope(file: KtFile): GlobalSearchScope? {
    val project = file.project
    val projectFileIndex = ProjectFileIndex.SERVICE.getInstance(project)
    val virtualFile = file.virtualFile ?: return null
    val moduleScope = projectFileIndex.getModuleForFile(virtualFile)?.moduleScope
    return when {
        moduleScope != null -> moduleScope
        projectFileIndex.isInLibrary(virtualFile) -> ProjectScope.getLibrariesScope(project)
        else -> null
    }
}