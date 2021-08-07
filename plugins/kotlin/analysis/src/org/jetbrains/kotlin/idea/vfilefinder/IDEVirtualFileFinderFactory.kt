// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import org.jetbrains.kotlin.idea.caches.project.ScriptModuleInfo
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory

class IDEVirtualFileFinderFactory : VirtualFileFinderFactory {
    override fun create(scope: GlobalSearchScope): VirtualFileFinder = IDEVirtualFileFinder(scope)

    override fun create(project: Project, module: ModuleDescriptor): VirtualFileFinder {
        val ideaModuleInfo = (module.getCapability(ModuleInfo.Capability) as? IdeaModuleInfo)

        val scope = when (ideaModuleInfo) {
            is ScriptModuleInfo -> GlobalSearchScope.union(
                ideaModuleInfo.dependencies().map { it.contentScope() }.toTypedArray()
            )
            else -> GlobalSearchScope.allScope(project)
        }
        return IDEVirtualFileFinder(scope)
    }
}
