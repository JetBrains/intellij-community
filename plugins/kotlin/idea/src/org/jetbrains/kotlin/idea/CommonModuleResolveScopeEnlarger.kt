// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.getKotlinSourceRootType
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm

class CommonModuleResolveScopeEnlarger : ResolveScopeEnlarger() {
    override fun getAdditionalResolveScope(file: VirtualFile, project: Project): SearchScope? {
        val modulesWithFacet = ProjectFacetManager.getInstance(project).getModulesWithFacet(KotlinFacetType.TYPE_ID)
        if (modulesWithFacet.isEmpty()) return null
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        val module = projectFileIndex.getModuleForFile(file) ?: return null
        if (!module.platform.isCommon()) return null
        
        if (projectFileIndex.getKotlinSourceRootType(file) == null) {
            return null
        }

        val implementingModule = module.implementingModules.find { it.platform.isJvm() } ?: return null

        var result = GlobalSearchScope.EMPTY_SCOPE
        for (entry in ModuleRootManager.getInstance(implementingModule).orderEntries) {
            if (entry is JdkOrderEntry) {
                val scopeForSdk = LibraryScopeCache.getInstance(project).getScopeForSdk(entry)
                result = result.uniteWith(scopeForSdk)
            }
        }
        return result
    }
}