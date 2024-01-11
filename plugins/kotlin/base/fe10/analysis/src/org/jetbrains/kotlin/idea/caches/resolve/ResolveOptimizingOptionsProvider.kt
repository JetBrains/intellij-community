// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.resolve.scopes.optimization.OptimizingOptions

interface ResolveOptimizingOptionsProvider {
    fun getOptimizingOptions(project: Project, descriptor: ModuleDescriptor, moduleInfo: IdeaModuleInfo): OptimizingOptions?

    companion object {
        val EP_NAME = ExtensionPointName.create<ResolveOptimizingOptionsProvider>("org.jetbrains.kotlin.idea.caches.resolve.resolveOptimizingOptionsProvider")

        fun getOptimizingOptions(project: Project, descriptor: ModuleDescriptor, moduleInfo: IdeaModuleInfo): OptimizingOptions? {
            return EP_NAME.extensions.firstNotNullOfOrNull { extension ->
                extension.getOptimizingOptions(project, descriptor, moduleInfo)
            }
        }
    }
}
