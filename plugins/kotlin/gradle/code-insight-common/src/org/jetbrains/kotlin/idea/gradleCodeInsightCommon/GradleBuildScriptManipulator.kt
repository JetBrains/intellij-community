// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("GradleBuildScriptManipulatorUtils")

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription

val SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS = setOf("classpath", "compile", "api", "implementation", "compileOnly", "runtimeOnly")
    @ApiStatus.Internal get

interface GradleBuildScriptManipulator<out Psi : PsiFile> {
    fun isApplicable(file: PsiFile): Boolean

    val scriptFile: Psi
    val preferNewSyntax: Boolean

    fun isConfiguredWithOldSyntax(kotlinPluginName: String): Boolean
    fun isConfigured(kotlinPluginExpression: String): Boolean

    fun configureModuleBuildScript(
        kotlinPluginName: String,
        kotlinPluginExpression: String,
        stdlibArtifactName: String,
        version: IdeKotlinVersion,
        jvmTarget: String?
    ): Boolean

    fun configureProjectBuildScript(kotlinPluginName: String, version: IdeKotlinVersion): Boolean

    fun changeLanguageFeatureConfiguration(feature: LanguageFeature, state: LanguageFeature.State, forTests: Boolean): PsiElement?

    fun changeLanguageVersion(version: String, forTests: Boolean): PsiElement?

    fun changeApiVersion(version: String, forTests: Boolean): PsiElement?

    fun addKotlinLibraryToModuleBuildScript(
        targetModule: Module?,
        scope: DependencyScope,
        libraryDescriptor: ExternalLibraryDescriptor
    )

    fun getKotlinStdlibVersion(): String?

    // For settings.gradle/settings.gradle.kts

    fun addMavenCentralPluginRepository()
    fun addPluginRepository(repository: RepositoryDescription)

    fun addResolutionStrategy(pluginId: String)
}

fun GradleBuildScriptManipulator<*>.usesNewMultiplatform(): Boolean {
    val fileText = runReadAction { scriptFile.text }
    return fileText.contains("multiplatform")
}

val MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX: GradleVersion = GradleVersion.version("4.4")
    @ApiStatus.Internal get

fun GradleBuildScriptManipulator<*>.useNewSyntax(kotlinPluginName: String, gradleVersion: GradleVersionInfo): Boolean {
    if (!preferNewSyntax) return false

    if (gradleVersion < GradleVersionProvider.getVersion(MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX.version)) return false

    if (isConfiguredWithOldSyntax(kotlinPluginName)) return false

    val fileText = runReadAction { scriptFile.text }
    val hasOldApply = fileText.contains("apply plugin:")

    return !hasOldApply
}

fun LanguageFeature.State.assertApplicableInMultiplatform() {
    if (this == LanguageFeature.State.DISABLED)
        throw UnsupportedOperationException("Disabling the language feature is unsupported for multiplatform")
}