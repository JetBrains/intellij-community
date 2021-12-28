// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinVersionVerbose
import org.jetbrains.kotlin.idea.util.buildNumber
import org.jetbrains.kotlin.util.firstNotNullResult

@Service(Service.Level.PROJECT)
class KotlinExternalCompilerVersionService(private val project: Project) {
    fun anyExternalCompilerVersion(): KotlinVersionVerbose? = ModuleManager.getInstance(project)
        .modules
        .firstNotNullResult(::externalCompilerVersion)

    fun externalCompilerVersion(module: Module): KotlinVersionVerbose? {
        val externalCompilerVersion = (if (module.getBuildSystemType() == BuildSystemType.JPS) {
            buildNumber
        } else {
            module.externalCompilerVersion
        }) ?: return null

        return KotlinVersionVerbose.parse(externalCompilerVersion)
    }
}

private fun Project.kotlinExternalCompilerVersionService() = this.service<KotlinExternalCompilerVersionService>()

fun Project.findAnyExternalKotlinCompilerVersion(): KotlinVersionVerbose? =
    kotlinExternalCompilerVersionService().anyExternalCompilerVersion()

fun Module.findExternalKotlinCompilerVersion(): KotlinVersionVerbose? =
    project.kotlinExternalCompilerVersionService().externalCompilerVersion(this)