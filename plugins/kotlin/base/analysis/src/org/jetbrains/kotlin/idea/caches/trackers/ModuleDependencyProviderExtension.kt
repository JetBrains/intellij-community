// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.Processor

interface ModuleDependencyProviderExtension {
    @Deprecated("Use #processAdditionalDependencyModules", ReplaceWith("processAdditionalDependencyModules(module, processor)"))
    fun getAdditionalDependencyModules(module: Module): Collection<Module>

    fun processAdditionalDependencyModules(module: Module, processor: Processor<Module>) {
        @Suppress("DEPRECATION")
        getAdditionalDependencyModules(module).forEach(processor::process)
    }

    companion object {
        val Default = object : ModuleDependencyProviderExtension {
            @Deprecated(
                "Use #processAdditionalDependencyModules",
                replaceWith = ReplaceWith("processAdditionalDependencyModules(module, processor)"),
            )
            override fun getAdditionalDependencyModules(module: Module): Collection<Module> = emptySet()
        }

        fun getInstance(project: Project): ModuleDependencyProviderExtension = project.service()
    }
}