// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.plugin.KotlinCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.util.isGradleModule
import org.jetbrains.kotlin.idea.gradle.configuration.readGradleProperty
import org.jetbrains.plugins.gradle.settings.GradleSettings

internal class GradleKotlinCompilerReferenceIndexApplicability : BtaKotlinCompilerReferenceIndexApplicabilityProvider {
    // TODO KTIJ-40389: Use the effective CRI generation value from the new Kotlin Gradle import once available
    // This approximation ignores `kotlin.compiler.runViaBuildToolsApi`, CI detection, and other property sources
    override fun isApplicable(project: Project): Boolean = runReadActionBlocking {
        val kotlinGradlePluginVersions = ModuleManager.getInstance(project).modules
            .filter(Module::isGradleModule)
            .mapNotNull(KotlinCompilerVersionProvider::getVersion)
        GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty() && isGradleCriGenerationEnabled(
            readGradleProperty(project, KOTLIN_CRI_GENERATION_PROPERTY),
            kotlinGradlePluginVersions
        )
    }
}
