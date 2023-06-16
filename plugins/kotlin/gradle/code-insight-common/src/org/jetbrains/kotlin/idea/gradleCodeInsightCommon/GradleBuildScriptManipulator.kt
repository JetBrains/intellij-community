// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("GradleBuildScriptManipulatorUtils")

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription
import org.jetbrains.kotlin.tools.projectWizard.Versions

val SCRIPT_PRODUCTION_DEPENDENCY_STATEMENTS = setOf("classpath", "compile", "api", "implementation", "compileOnly", "runtimeOnly")
    @ApiStatus.Internal get

val FOOJAY_RESOLVER_NAME = "org.gradle.toolchains.foojay-resolver"
    @ApiStatus.Internal get

val FOOJAY_RESOLVER_CONVENTION_NAME = "org.gradle.toolchains.foojay-resolver-convention"
    @ApiStatus.Internal get

typealias ChangedFiles = HashSet<PsiFile>

typealias ChangedSettingsFile = PsiFile?

interface GradleBuildScriptManipulator<out Psi : PsiFile> {
    fun isApplicable(file: PsiFile): Boolean

    val scriptFile: Psi
    val preferNewSyntax: Boolean

    fun isConfiguredWithOldSyntax(kotlinPluginName: String): Boolean
    fun isConfigured(kotlinPluginExpression: String): Boolean

    fun configureBuildScripts(
        kotlinPluginName: String,
        kotlinPluginExpression: String,
        stdlibArtifactName: String,
        addVersion: Boolean,
        version: IdeKotlinVersion,
        jvmTarget: String?
    ): ChangedFiles

    fun configureProjectBuildScript(kotlinPluginName: String, version: IdeKotlinVersion): Boolean

    fun isKotlinConfiguredInBuildScript(): Boolean

    fun changeLanguageFeatureConfiguration(feature: LanguageFeature, state: LanguageFeature.State, forTests: Boolean): PsiElement?

    fun changeLanguageVersion(version: String, forTests: Boolean): PsiElement?

    fun changeApiVersion(version: String, forTests: Boolean): PsiElement?

    fun addKotlinLibraryToModuleBuildScript(
        targetModule: Module?,
        scope: DependencyScope,
        libraryDescriptor: ExternalLibraryDescriptor
    )

    fun getKotlinStdlibVersion(): String?

    fun configureJvmTarget(jvmTarget: String, version: IdeKotlinVersion): ChangedSettingsFile

    fun addFoojayPlugin(): ChangedSettingsFile

    fun addJdkSpec(
        jvmTarget: String,
        version: IdeKotlinVersion,
        gradleVersion: GradleVersionInfo,
        applySpec: (
            useToolchain: Boolean,
            useToolchainHelper: Boolean,
            targetVersionNumber: String
        ) -> Unit
    ) {
        val useToolchain =
            gradleVersion >= GradleVersionProvider.getVersion(Versions.GRADLE_PLUGINS.MIN_GRADLE_FOOJAY_VERSION.text)
                    && version.compare("1.5.30") >= 0
        val useToolchainHelper = useToolchain && version.compare("1.7.20") >= 0

        val targetVersion = getJvmTargetVersion(jvmTarget, version, useToolchain, useToolchainHelper)
        applySpec(useToolchain, useToolchainHelper, targetVersion)
    }

    fun getJvmTargetVersion(
        jvmTarget: String,
        version: IdeKotlinVersion,
        useToolchain: Boolean,
        useToolchainHelper: Boolean
    ): String {
        var targetVersion = jvmTarget

        val targetVersionNumber = jvmTarget.removePrefix("1.").toIntOrNull() ?: return targetVersion

        // Kotlin 1.7.0+ and toolchains support JVM target = 1.8+
        if (version.compare("1.7.0") >= 0 || useToolchain || useToolchainHelper) {
            if (targetVersionNumber < 8) {
                targetVersion = JvmTarget.JVM_1_8.description
            }
        }

        if (useToolchain || useToolchainHelper) {
            targetVersion = targetVersion.removePrefix("1.")
        }
        return targetVersion
    }

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
