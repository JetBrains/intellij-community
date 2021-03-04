/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.NonClasspathClassFinder
import com.intellij.psi.PsiClass
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.kotlin.resolve.jvm.KotlinSafeClassFinder

class KotlinScriptDependenciesClassFinder(
    private val project: Project
) : NonClasspathClassFinder(project), KotlinSafeClassFinder {
    override fun calcClassRoots(): List<VirtualFile> {
        return ScriptConfigurationManager.getInstance(project)
            .getAllScriptsDependenciesClassFiles()
            .filter { it.isValid }
    }

    private val everywhereCache = CachedValuesManager.getManager(project).createCachedValue {
        CachedValueProvider.Result.create(
            ConcurrentFactoryMap.createMap { qualifiedName: String ->
                findClassInternal(qualifiedName, GlobalSearchScope.everythingScope(project))
            },
            PsiModificationTracker.MODIFICATION_COUNT,
            ProjectRootModificationTracker.getInstance(project),
            VirtualFileManager.getInstance()
        )
    }

    override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
        // The most important part for performance
        val cachedClass = everywhereCache.value[qualifiedName] ?: return null
        if (isInScope(cachedClass, scope)) {
            return cachedClass
        }

        return findClassInternal(qualifiedName, scope)
    }

    private fun findClassInternal(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
        val topLevelClass = super.findClass(qualifiedName, scope)
        if (topLevelClass != null && isInScope(topLevelClass, scope)) {
            return topLevelClass
        }

        // The following code is needed because NonClasspathClassFinder cannot find inner classes
        // JavaFullClassNameIndex cannot be used directly, because it filter only classes in source roots

        val classes = StubIndex.getElements(
            JavaFullClassNameIndex.getInstance().key,
            qualifiedName.hashCode(),
            project,
            scope.takeUnless { it is EverythingGlobalScope },
            PsiClass::class.java
        ).filter {
            it.qualifiedName == qualifiedName
        }

        return when (classes.size) {
            0 -> null
            1 -> classes.single()
            else -> classes.first()  // todo: check when this happens
        }?.takeIf { isInScope(it, scope) }
    }

    private fun isInScope(clazz: PsiClass, scope: GlobalSearchScope): Boolean {
        if (scope is EverythingGlobalScope) {
            return true
        }

        val file = clazz.containingFile?.virtualFile ?: return false
        val index = ProjectFileIndex.SERVICE.getInstance(myProject)
        return !index.isInContent(file) && !index.isInLibrary(file) && scope.contains(file)
    }
}