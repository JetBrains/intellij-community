// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.psi.UserDataProperty

object ExternalCompilerVersionProvider {
    private var Module.externalCompilerVersion: String? by UserDataProperty(Key.create("EXTERNAL_COMPILER_VERSION"))

    fun get(module: Module): IdeKotlinVersion? {
        val rawVersion = module.externalCompilerVersion ?: return null
        return IdeKotlinVersion.opt(rawVersion)
    }

    @ApiStatus.Internal
    fun set(module: Module, version: IdeKotlinVersion?) {
        module.externalCompilerVersion = version?.rawVersion
    }

    fun findAll(project: Project): Set<IdeKotlinVersion> {
        val result = LinkedHashSet<IdeKotlinVersion>()
        var hasJpsModules = false

        runReadAction {
            for (module in ModuleManager.getInstance(project).modules) {
                if (module.buildSystemType == BuildSystemType.JPS) {
                    if (!hasJpsModules) hasJpsModules = true
                } else {
                    val externalVersion = module.externalCompilerVersion?.let(IdeKotlinVersion::opt)
                    if (externalVersion != null) {
                        result.add(externalVersion)
                    }
                }
            }
        }

        if (hasJpsModules) {
            val projectGlobalVersion = IdeKotlinVersion.opt(KotlinJpsPluginSettings.jpsVersion(project))
            if (projectGlobalVersion != null) {
                result.add(projectGlobalVersion)
            }
        }

        return result
    }

    fun findLatest(project: Project): IdeKotlinVersion? {
        return findAll(project).maxOrNull()
    }
}