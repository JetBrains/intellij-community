// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.trackers

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.getServiceSafe

interface ModuleDependencyProviderExtension {
    fun getAdditionalDependencyModules(module: Module): Collection<Module>

    companion object {
        val Default = object : ModuleDependencyProviderExtension {
            override fun getAdditionalDependencyModules(module: Module): Collection<Module> = emptySet()
        }

        fun getInstance(project: Project): ModuleDependencyProviderExtension = project.getServiceSafe()
    }
}