// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory

class IdeVirtualFileFinderFactory : VirtualFileFinderFactory {
    override fun create(scope: GlobalSearchScope): VirtualFileFinder = IdeVirtualFileFinder(scope)

    override fun create(project: Project, module: ModuleDescriptor): VirtualFileFinder {
        val ideaModuleInfo = module.getCapability(ModuleInfo.Capability) as? IdeaModuleInfo

        val scope = if (ideaModuleInfo is ScriptModuleInfo) {
            val moduleDependenciesScope = ideaModuleInfo.dependencies().map { it.contentScope }
            GlobalSearchScope.union(moduleDependenciesScope)
        } else {
            GlobalSearchScope.allScope(project)
        }

        return IdeVirtualFileFinder(scope)
    }
}
