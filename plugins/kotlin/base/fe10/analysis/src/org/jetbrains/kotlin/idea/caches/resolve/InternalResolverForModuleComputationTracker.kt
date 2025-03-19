// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForModuleComputationTracker
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo

class InternalResolverForModuleComputationTracker: ResolverForModuleComputationTrackerEx {
    override fun onResolverComputed(moduleInfo: ModuleInfo) {
        LOG.info("computed $moduleInfo")
    }

    override fun onCreateResolverForModule(descriptor: ModuleDescriptor, moduleInfo: IdeaModuleInfo) {
        LOG.info("creating resolver for $descriptor $moduleInfo")
    }

    companion object {
        internal val LOG = Logger.getInstance(InternalResolverForModuleComputationTracker::class.java)
    }
}

interface ResolverForModuleComputationTrackerEx: ResolverForModuleComputationTracker {

    fun onCreateResolverForModule(descriptor: ModuleDescriptor, moduleInfo: IdeaModuleInfo)

    companion object {
        @Suppress("IncorrectServiceRetrieving")
        fun getInstance(project: Project): ResolverForModuleComputationTrackerEx? =
            if (enabled) project.getService(ResolverForModuleComputationTrackerEx::class.java) else null
    }
}

private val enabled = ApplicationManagerEx.isInIntegrationTest()
