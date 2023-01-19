// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
import org.jetbrains.kotlin.idea.base.util.caching.ModuleEntityChangeListener
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex

abstract class FrameworkAvailabilityChecker(
    project: Project
) : SynchronizedFineGrainedEntityCache<FrameworkAvailabilityChecker.CompoundKey, Boolean>(project) {
    data class CompoundKey(val module: Module, val includeTests: Boolean)

    protected abstract val fqNames: Set<String>

    protected abstract val javaClassLookup: Boolean
    protected abstract val aliasLookup: Boolean
    protected abstract val kotlinFullClassLookup: Boolean

    fun get(module: Module, includeTests: Boolean): Boolean {
        return get(CompoundKey(module, includeTests))
    }

    override fun subscribe() {
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, ModelChangeListener(project))
    }

    override fun checkKeyValidity(key: CompoundKey) {
        val module = key.module
        if (module.isDisposed) {
            throw IllegalStateException("Module ${module.name} is already disposed")
        }
    }

    override fun calculate(key: CompoundKey): Boolean {
        val moduleScope = key.module.getModuleWithDependenciesAndLibrariesScope(key.includeTests)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)

        return fqNames.any { fqName ->
            (javaClassLookup && javaPsiFacade.findClass(fqName, moduleScope) != null)
                    || (aliasLookup && KotlinTopLevelTypeAliasFqNameIndex.get(fqName, project, moduleScope).isNotEmpty())
                    || (kotlinFullClassLookup && KotlinFullClassNameIndex.get(fqName, project, moduleScope).isNotEmpty())
        }
    }

    private inner class ModelChangeListener(project: Project) : ModuleEntityChangeListener(project) {
        override fun entitiesChanged(outdated: List<Module>) {
            val checkerClass = this@FrameworkAvailabilityChecker.javaClass
            val service = project.getService(checkerClass) ?: error("Cannot find service $checkerClass")
            service.invalidateEntries(condition = { key, _ -> key.module in outdated })
        }
    }
}

inline fun <reified T : FrameworkAvailabilityChecker> isFrameworkAvailable(element: PsiElement): Boolean {
    return isFrameworkAvailable(T::class.java, element)
}

fun <T : FrameworkAvailabilityChecker> isFrameworkAvailable(checkerClass: Class<T>, element: PsiElement): Boolean {
    val index = ProjectFileIndex.getInstance(element.project)
    val virtualFile = element.containingFile.virtualFile
    val module = index.getModuleForFile(virtualFile) ?: return false
    return isFrameworkAvailable(checkerClass, module, includeTests = index.isInTestSourceContent(virtualFile))
}

private fun <T : FrameworkAvailabilityChecker> isFrameworkAvailable(checkerClass: Class<T>, module: Module, includeTests: Boolean): Boolean {
    val checker = module.project.getService(checkerClass) ?: error("Cannot find service $checkerClass")
    return checker.get(module, includeTests)
}