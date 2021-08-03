// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.script

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.Processor
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptMarkerFileSystem
import org.jetbrains.kotlin.resolve.jvm.KotlinSafeClassFinder

class KotlinScriptDependenciesClassFinder(private val project: Project) : NonClasspathClassFinder(project), KotlinSafeClassFinder {
    private val useOnlyForScripts = Registry.`is`("kotlin.resolve.scripting.limit.dependency.element.finder", true)

    /*
        'PsiElementFinder's are global and can be called for any context.
        As 'KotlinScriptDependenciesClassFinder' is meant to provide additional dependencies only for scripts,
        we need to know if the caller came from a script resolution context.

        We are doing so by checking if the given scope contains a synthetic 'KotlinScriptMarkerFileSystem.rootFile'.
        Normally, only global scopes and 'KotlinScriptScope' contains such a file.
    */
    private fun isApplicable(scope: GlobalSearchScope): Boolean {
        return !useOnlyForScripts || scope.contains(KotlinScriptMarkerFileSystem.rootFile)
    }

    override fun getClassRoots(scope: GlobalSearchScope?): List<VirtualFile> {
        var result = super.getClassRoots(scope)
        if (scope is EverythingGlobalScope) {
            result = result + KotlinScriptMarkerFileSystem.rootFile
        }
        return result
    }

    override fun calcClassRoots(): List<VirtualFile> {
        return ScriptConfigurationManager.getInstance(project)
            .getAllScriptsDependenciesClassFiles()
            .filter { it.isValid }
    }

    private val everywhereCache = CachedValuesManager.getManager(project).createCachedValue {
        CachedValueProvider.Result.create(
            ConcurrentFactoryMap.createMap { qualifiedName: String ->
                findClassNotCached(qualifiedName, GlobalSearchScope.everythingScope(project))
            },
            ScriptDependenciesModificationTracker.getInstance(project),
            PsiModificationTracker.MODIFICATION_COUNT,
            ProjectRootModificationTracker.getInstance(project),
            VirtualFileManager.getInstance()
        )
    }

    override fun findClass(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
        return if (isApplicable(scope)) everywhereCache.value[qualifiedName]?.takeIf { isInScope(it, scope) } else null
    }

    private fun findClassNotCached(qualifiedName: String, scope: GlobalSearchScope): PsiClass? {
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

    override fun findClasses(qualifiedName: String, scope: GlobalSearchScope): Array<PsiClass> {
        return if (isApplicable(scope)) super.findClasses(qualifiedName, scope) else emptyArray()
    }

    override fun getSubPackages(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiPackage> {
        return if (isApplicable(scope)) super.getSubPackages(psiPackage, scope) else emptyArray()
    }

    override fun getClasses(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiClass> {
        return if (isApplicable(scope)) super.getClasses(psiPackage, scope) else emptyArray()
    }

    override fun getPackageFiles(psiPackage: PsiPackage, scope: GlobalSearchScope): Array<PsiFile> {
        return if (isApplicable(scope)) super.getPackageFiles(psiPackage, scope) else emptyArray()
    }

    override fun getPackageFilesFilter(psiPackage: PsiPackage, scope: GlobalSearchScope): Condition<PsiFile>? {
        return if (isApplicable(scope)) super.getPackageFilesFilter(psiPackage, scope) else null
    }

    override fun getClassNames(psiPackage: PsiPackage, scope: GlobalSearchScope): Set<String> {
        return if (isApplicable(scope)) super.getClassNames(psiPackage, scope) else emptySet()
    }

    override fun processPackageDirectories(
        psiPackage: PsiPackage, scope: GlobalSearchScope,
        consumer: Processor<in PsiDirectory>, includeLibrarySources: Boolean
    ): Boolean {
        return if (isApplicable(scope)) super.processPackageDirectories(psiPackage, scope, consumer, includeLibrarySources) else true
    }
}