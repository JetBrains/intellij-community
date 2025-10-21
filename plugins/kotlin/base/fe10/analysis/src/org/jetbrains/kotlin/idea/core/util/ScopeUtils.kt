// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ScopeUtilsKt")

package org.jetbrains.kotlin.idea.core.util

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.base.projectStructure.KotlinResolveScopeEnlarger
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SourceForBinaryModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.scope.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.core.script.v1.ScriptModuleInfo
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile

//NOTE: idea default API returns module search scope for file under module but not in source or production source (for example, test data )
// this scope can't be used to search for kotlin declarations in index in order to resolve in that case
// see com.intellij.psi.impl.file.impl.ResolveScopeManagerImpl.getInherentResolveScope
fun getResolveScope(file: KtFile): GlobalSearchScope {
    if (file is KtCodeFragment) {
        // Scope should be corrected when KT-6223 is implemented
        val contextScope = file.getContextContainingFile()?.resolveScope
        if (contextScope != null) {
            @OptIn(K1ModeProjectStructureApi::class)
            return when (file.moduleInfo) {
                is SourceForBinaryModuleInfo -> KotlinSourceFilterScope.libraryClasses(contextScope, file.project)
                else -> KotlinSourceFilterScope.projectSourcesAndLibraryClasses(contextScope, file.project)
            }
        }
    }

    @OptIn(K1ModeProjectStructureApi::class)
    return when (file.moduleInfo) {
        is ModuleSourceInfo -> {
            val projectScope = KotlinSourceFilterScope.projectFiles(file.resolveScope, file.project)
            KotlinResolveScopeEnlarger.Companion.enlargeScope(projectScope, file)
        }
        is ScriptModuleInfo -> {
            val dependencyScopes = file.moduleInfo.dependencies().map { it.contentScope }
            GlobalSearchScope.union(dependencyScopes)
        }
        else -> GlobalSearchScope.EMPTY_SCOPE
    }
}