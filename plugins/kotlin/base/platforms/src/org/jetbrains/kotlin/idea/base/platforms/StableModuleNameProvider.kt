// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.platforms

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

interface StableModuleNameProvider {
    fun getStableModuleName(module: Module): String

    companion object {
        private val LOG = Logger.getInstance(StableModuleNameProvider::class.java)

        private val Fallback = object : StableModuleNameProvider {
            override fun getStableModuleName(module: Module): String {
                LOG.error("HMPP: regular workspace module name was used for module $module")
                return module.name
            }
        }

        fun getInstance(project: Project): StableModuleNameProvider {
            return project.getService(StableModuleNameProvider::class.java) ?: Fallback
        }
    }
}
