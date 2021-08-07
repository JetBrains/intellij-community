// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.extensions.gradle

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.module

data class RepositoryDescription(
    val id: String,
    val name: String,
    val url: String,
    val bintrayUrl: String?,
    val isSnapshot: Boolean
)

val SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS = setOf("classpath", "compile", "api", "implementation", "compileOnly", "runtimeOnly")

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

fun GradleBuildScriptManipulator<*>.usesNewMultiplatform(): Boolean {
    val fileText = runReadAction { scriptFile.text }
    return fileText.contains("multiplatform")
}

interface GradleVersionInfo : Comparable<GradleVersionInfo>

interface GradleVersionProvider {
    fun getVersion(versionString: String): GradleVersionInfo

    fun getCurrentVersionGlobal(): GradleVersionInfo
    fun getCurrentVersion(project: Project, path: String): GradleVersionInfo?
}

fun GradleVersionProvider.fetchGradleVersion(psiFile: PsiFile): GradleVersionInfo {
    return gradleVersionFromFile(psiFile) ?: getCurrentVersionGlobal()
}

private fun GradleVersionProvider.gradleVersionFromFile(psiFile: PsiFile): GradleVersionInfo? {
    val module = psiFile.module ?: return null
    val path = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    return getCurrentVersion(module.project, path)
}

const val MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX_RAW: String = "4.4"
private const val MIN_GRADLE_VERSION_FOR_API_AND_IMPLEMENTATION_RAW: String = "3.4"

fun GradleBuildScriptManipulator<*>.useNewSyntax(
    kotlinPluginName: String,
    gradleVersion: GradleVersionInfo,
    versionProvider: GradleVersionProvider
): Boolean {
    if (!preferNewSyntax) return false

    if (gradleVersion < versionProvider.getVersion(MIN_GRADLE_VERSION_FOR_NEW_PLUGIN_SYNTAX_RAW)) return false

    if (isConfiguredWithOldSyntax(kotlinPluginName)) return false

    val fileText = runReadAction { scriptFile.text }
    val hasOldApply = fileText.contains("apply plugin:")

    return !hasOldApply
}

fun LanguageFeature.State.assertApplicableInMultiplatform() {
    if (this == LanguageFeature.State.ENABLED_WITH_ERROR || this == LanguageFeature.State.DISABLED)
        throw UnsupportedOperationException("Disabling the language feature is unsupported for multiplatform")
}

fun GradleVersionInfo.scope(directive: String, versionProvider: GradleVersionProvider): String {
    if (this < versionProvider.getVersion(MIN_GRADLE_VERSION_FOR_API_AND_IMPLEMENTATION_RAW)) {
        return when (directive) {
            "implementation" -> "compile"
            "testImplementation" -> "testCompile"
            else -> throw IllegalArgumentException("Unknown directive `$directive`")
        }
    }

    return directive
}
