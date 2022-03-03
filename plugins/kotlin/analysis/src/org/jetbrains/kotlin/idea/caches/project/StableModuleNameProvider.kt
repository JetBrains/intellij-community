// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

interface StableModuleNameProvider {
    fun getStableModuleName(module: Module): String

    companion object {
        val Fallback = object : StableModuleNameProvider {
            override fun getStableModuleName(module: Module): String {
                LOG.error("HMPP: regular workspace module name was used for module $module")
                return module.name
            }
        }

        fun getInstance(project: Project): StableModuleNameProvider =
            project.getService(StableModuleNameProvider::class.java) ?: Fallback
    }
}
