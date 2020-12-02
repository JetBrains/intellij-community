// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.plugins.gradle.settings.GradleSettings

interface GradleBuildScriptManipulator<out Psi : PsiFile> {
    val scriptFile: Psi
    val preferNewSyntax: Boolean

    fun isConfiguredWithOldSyntax(kotlinPluginName: String): Boolean
    fun isConfigured(kotlinPluginExpression: String): Boolean

    fun configureModuleBuildScript(
        kotlinPluginName: String,
        kotlinPluginExpression: String,
        stdlibArtifactName: String,
        version: String,
        jvmTarget: String?
    ): Boolean

    fun configureProjectBuildScript(kotlinPluginName: String, version: String): Boolean

    fun changeLanguageFeatureConfiguration(feature: LanguageFeature, state: LanguageFeature.State, forTests: Boolean): PsiElement?

    fun changeLanguageVersion(version: String, forTests: Boolean): PsiElement?

    fun changeApiVersion(version: String, forTests: Boolean): PsiElement?

    fun addKotlinLibraryToModuleBuildScript(
        targetModule: Module?,
        scope: DependencyScope,
        libraryDescriptor: ExternalLibraryDescriptor
    )

    @Deprecated(
        "Can't work with multiplatform projects",
        ReplaceWith("addKotlinLibraryToModuleBuildScript(null, scope, libraryDescriptor)")
    )
    fun addKotlinLibraryToModuleBuildScript(
        scope: DependencyScope,
        libraryDescriptor: ExternalLibraryDescriptor
    )

    fun getKotlinStdlibVersion(): String?

    // For settings.gradle/settings.gradle.kts

    fun addMavenCentralPluginRepository()
    fun addPluginRepository(repository: RepositoryDescription)

    fun addResolutionStrategy(pluginId: String)
}

fun fetchGradleVersion(psiFile: PsiFile): GradleVersion {
    return gradleVersionFromFile(psiFile) ?: GradleVersion.current()
}

private fun gradleVersionFromFile(psiFile: PsiFile): GradleVersion? {
    val module = psiFile.module ?: return null
    val path = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    return GradleSettings.getInstance(module.project).getLinkedProjectSettings(path)?.resolveGradleVersion()
}

val MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX = GradleVersion.version("4.4")

fun GradleBuildScriptManipulator<*>.useNewSyntax(kotlinPluginName: String, gradleVersion: GradleVersion): Boolean {
    if (!preferNewSyntax) return false

    if (gradleVersion < MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX) return false

    if (isConfiguredWithOldSyntax(kotlinPluginName)) return false

    val fileText = runReadAction { scriptFile.text }
    val hasOldApply = fileText.contains("apply plugin:")

    return !hasOldApply
}

fun GradleBuildScriptManipulator<*>.usesNewMultiplatform(): Boolean {
    val fileText = runReadAction { scriptFile.text }
    return fileText.contains("multiplatform")
}

fun LanguageFeature.State.assertApplicableInMultiplatform() {
    if (this == LanguageFeature.State.ENABLED_WITH_ERROR || this == LanguageFeature.State.DISABLED)
        throw UnsupportedOperationException("Disabling the language feature is unsupported for multiplatform")
}

private val MIN_GRADLE_VERSION_FOR_API_AND_IMPLEMENTATION = GradleVersion.version("3.4")

fun GradleVersion.scope(directive: String): String {
    if (this < MIN_GRADLE_VERSION_FOR_API_AND_IMPLEMENTATION) {
        return when (directive) {
            "implementation" -> "compile"
            "testImplementation" -> "testCompile"
            else -> throw IllegalArgumentException("Unknown directive `$directive`")
        }
    }

    return directive
}
