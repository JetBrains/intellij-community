// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

interface ModuleDependencyProviderExtension {
    @Deprecated("Use #processAdditionalDependencyModules", ReplaceWith("processAdditionalDependencyModules(module, processor)"))
    fun getAdditionalDependencyModules(module: Module): Collection<Module>

    fun processAdditionalDependencyModules(module: Module, processor: Processor<Module>) {
        getAdditionalDependencyModules(module).forEach(processor::process)
    }

    companion object {
        val Default = object : ModuleDependencyProviderExtension {
            override fun getAdditionalDependencyModules(module: Module): Collection<Module> = emptySet()
        }

        fun getInstance(project: Project): ModuleDependencyProviderExtension = project.getServiceSafe()
    }
}