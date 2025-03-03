// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaResolutionScopeProvider
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.load.kotlin.MetadataFinderFactory
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinder
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory

class IdeVirtualFileFinderFactory(private val project: Project) : VirtualFileFinderFactory, MetadataFinderFactory {
    override fun create(scope: GlobalSearchScope): VirtualFileFinder = IdeVirtualFileFinder(scope, project)

    @OptIn(KaImplementationDetail::class)
    override fun create(project: Project, module: ModuleDescriptor): VirtualFileFinder {
        val ideaModuleInfo = module.getCapability(ModuleInfo.Capability) as? IdeaModuleInfo

        val scope = if (ideaModuleInfo != null) {
            KaResolutionScopeProvider.getInstance(project).getResolutionScope(ideaModuleInfo.toKaModule())
        } else {
            GlobalSearchScope.allScope(project)
        }

        return IdeVirtualFileFinder(scope, project)
    }
}
