// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelTypeAliasFqNameIndex

open class AvailabilityProvider(
    private val test: Boolean,
    private val fqNames: Set<String>,
    private val javaClassLookup: Boolean = true,
    private val aliasLookup: Boolean = true,
    private val kotlinFullClassLookup: Boolean = true,
) : ParameterizedCachedValueProvider<Boolean, Module> {
    override fun compute(module: Module): CachedValueProvider.Result<Boolean> {
        val project = module.project
        val moduleScope = module.getModuleWithDependenciesAndLibrariesScope(test)
        val javaPsiFacade = JavaPsiFacade.getInstance(project)
        val hasKotlinTest =
            fqNames.any {
                javaClassLookup && javaPsiFacade.findClass(it, moduleScope) != null ||
                        aliasLookup && KotlinTopLevelTypeAliasFqNameIndex.get(it, project, moduleScope).isNotEmpty() ||
                        kotlinFullClassLookup && KotlinFullClassNameIndex.get(it, project, moduleScope).isNotEmpty()
            }

        return CachedValueProvider.Result.create(hasKotlinTest, ProjectRootManager.getInstance(project))
    }
}

fun PsiElement.isClassAvailableInModule(
    key: Key<ParameterizedCachedValue<Boolean, Module>>,
    nonTestValueProvider: ParameterizedCachedValueProvider<Boolean, Module>,
    testValueProvider: ParameterizedCachedValueProvider<Boolean, Module>
): Boolean? {
    val index = ProjectFileIndex.getInstance(project)
    val virtualFile = this.containingFile.virtualFile
    return index.getModuleForFile(virtualFile)?.let { module ->
        val availabilityProvider =
            if (index.isInTestSourceContent(virtualFile)) testValueProvider else
                nonTestValueProvider

        val available = CachedValuesManager.getManager(project)
            .getParameterizedCachedValue(module, key, availabilityProvider, false, module)
        available
    }
}