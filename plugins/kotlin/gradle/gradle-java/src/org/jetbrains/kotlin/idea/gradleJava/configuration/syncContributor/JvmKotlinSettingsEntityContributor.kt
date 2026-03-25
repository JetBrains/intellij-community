// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.syncContributor

import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.convertPathsToSystemIndependent
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.facet.noVersionAutoAdvance
import org.jetbrains.kotlin.idea.facet.updateCompilerSettings
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.serialization.KotlinFacetSettingsWorkspaceModel
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntityBuilder
import org.jetbrains.kotlin.idea.workspaceModel.kotlinSettings
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncContributor.GradleSourceRootSyncContributorExtension
import org.jetbrains.plugins.gradle.service.syncContributor.GradleSourceSetSyncContext

/**
 * Configures a facet only if the platform kind is JVM.
 */
class JvmKotlinSettingsEntityContributor: GradleSourceRootSyncContributorExtension {
    override suspend fun configureSourceSetModules(context: GradleSourceSetSyncContext) {
        val kotlinGradleModel = context.resolverContext.getProjectModel(context.projectModel, KotlinGradleModel::class.java) ?: return
        if (kotlinGradleModel.platformPluginId != JvmIdePlatformKind.tooling.gradlePluginId) {
            return // only configure jvm platform kind
        }
        configureJvmKotlinSettingsEntity(context.resolverContext, context.projectModel, context.moduleEntity, context.sourceSetName)
    }
}

/**
 * Configures a facet assuming the platform kind is JVM.
 *
 * Exposed to other plugins explicitly, as the contributor above only configures Java modules.
 * This is expected to be called by other plugins (namely Android) to configure the Kotlin JVM facet.
 */
fun configureJvmKotlinSettingsEntity(
    context: ProjectResolverContext,
    projectModel: GradleLightProject,
    moduleEntity: ModuleEntityBuilder,
    sourceSetName: String
) {
    val kotlinGradleModel = context.getProjectModel(projectModel, KotlinGradleModel::class.java) ?: return
    val arguments: List<String> = kotlinGradleModel.compilerArgumentsBySourceSet[sourceSetName] ?: return
    val pluginVersion = kotlinGradleModel.kotlinGradlePluginVersion?.versionString

    val jvmArguments = K2JVMCompilerArguments().apply {
        parseCommandLineArguments(arguments, this)
        convertPathsToSystemIndependent()
    }
    moduleEntity.kotlinSettings += createEmptyKotlinSettingsEntity(moduleEntity) {
        with(KotlinFacetSettingsWorkspaceModel(this)) {
            targetPlatform = JvmIdePlatformKind.defaultPlatform
            updateCompilerSettings(jvmArguments)
            // Updating compiler settings might mutate jvm arguments, need to be done after that
            compilerArguments = jvmArguments

            // Setting defaults.
            updateCompilerArguments {
                if (apiVersion == null) apiVersion = pluginVersion
                if (languageVersion == null) languageVersion = pluginVersion

                if (pluginOptions == null) pluginOptions = emptyArray()
                if (pluginClasspaths == null) pluginClasspaths = emptyArray()

                autoAdvanceApiVersion = false
                autoAdvanceLanguageVersion = false
            }
        }
    }
}

private fun createEmptyKotlinSettingsEntity(
    moduleEntity: ModuleEntityBuilder,
    init: (KotlinSettingsEntityBuilder.() -> Unit)
) = KotlinSettingsEntity(
    name = KotlinFacetType.INSTANCE.presentableName,
    moduleId = ModuleId(moduleEntity.name),
    entitySource = moduleEntity.entitySource,
    version = KotlinFacetSettings.CURRENT_VERSION,
    kind = KotlinModuleKind.DEFAULT,
    externalProjectId = "",
    useProjectSettings = false,
    isHmppEnabled = false,
    flushNeeded = false,
    isTestModule = false,
    sourceRoots = emptyList(),
    configFileItems = emptyList(),
    implementedModuleNames = emptyList(),
    dependsOnModuleNames = emptyList(),
    additionalVisibleModuleNames = emptySet(),
    sourceSetNames = emptyList(),
    pureKotlinSourceFolders = emptyList(),
    externalSystemRunTasks = emptyList(),
    init = init
)


