// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.Processor

interface ModuleDependencyProviderExtension {
    fun processAdditionalDependencyModules(module: Module, processor: Processor<Module>)

    companion object {
        val Default = object : ModuleDependencyProviderExtension {
            override fun processAdditionalDependencyModules(module: Module, processor: Processor<Module>) {
            }
        }

        fun getInstance(project: Project): ModuleDependencyProviderExtension = project.service()
    }
}