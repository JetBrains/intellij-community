// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.common.arguments.ManualLanguageFeatureSetting
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.createArguments
import org.jetbrains.kotlin.idea.caches.project.isKpmModule
import org.jetbrains.kotlin.idea.caches.project.refinesFragmentIds
import org.jetbrains.kotlin.idea.configuration.externalCompilerVersion
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.gradleJava.KotlinGradleFacadeImpl.findKotlinPluginVersion
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.roots.findAll
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

class KotlinFragmentDataService : AbstractProjectDataService<KotlinFragmentData, Void>() {
    override fun getTargetDataKey(): Key<KotlinFragmentData> = KotlinFragmentData.KEY

    override fun postProcess(
        toImport: MutableCollection<out DataNode<KotlinFragmentData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        for (fragmentDataNode in toImport) {
            val sourceSetDataNode = ExternalSystemApiUtil.findParent(fragmentDataNode, GradleSourceSetData.KEY)
                ?: error("Failed to find parent GradleSourceSetData for KotlinKPMGradleFragmentData '${fragmentDataNode.data.externalName}'")

            val ideModule = modelsProvider.findIdeModule(sourceSetDataNode.data) ?: continue

            val moduleNode = ExternalSystemApiUtil.findParent(sourceSetDataNode, ProjectKeys.MODULE) ?: continue
            configureFacetByFragmentData(ideModule, modelsProvider, moduleNode, sourceSetDataNode, fragmentDataNode)?.also { kotlinFacet ->
                GradleProjectImportHandler.getInstances(project).forEach { it.importBySourceSet(kotlinFacet, sourceSetDataNode) }
            }
        }
    }

    companion object {
        private fun KotlinFacet.configureFacet(
            compilerVersion: String?,
            platform: TargetPlatform?, // if null, detect by module dependencies
            modelsProvider: IdeModifiableModelsProvider,
        ) {
            val module = module
            with(configuration.settings) {
                compilerArguments = null
                targetPlatform = null
                compilerSettings = null
                initializeIfNeeded(
                    module,
                    modelsProvider.getModifiableRootModel(module),
                    platform,
                    compilerVersion
                )
                val apiLevel = apiLevel
                val languageLevel = languageLevel
                if (languageLevel != null && apiLevel != null && apiLevel > languageLevel) {
                    this.apiLevel = languageLevel
                }
            }

            module.externalCompilerVersion = compilerVersion
        }

        private fun configureFacetByFragmentData(
            ideModule: Module,
            modelsProvider: IdeModifiableModelsProvider,
            moduleNode: DataNode<ModuleData>,
            sourceSetNode: DataNode<GradleSourceSetData>,
            fragmentDataNode: DataNode<KotlinFragmentData>
        ): KotlinFacet? {

            val compilerVersion = moduleNode.findAll(BuildScriptClasspathData.KEY).firstOrNull()?.data?.let(::findKotlinPluginVersion)
                ?: return null

            val platform = when (fragmentDataNode.data.platform) {
                KotlinPlatform.COMMON -> CommonPlatforms.defaultCommonPlatform
                KotlinPlatform.JVM, KotlinPlatform.ANDROID -> JvmPlatforms.defaultJvmPlatform
                KotlinPlatform.JS -> JsPlatforms.defaultJsPlatform
                KotlinPlatform.NATIVE -> NativePlatforms.unspecifiedNativePlatform
            }

            val languageSettings = fragmentDataNode.data.languageSettings

            val compilerArguments = platform.createArguments {
                multiPlatform = true
                languageSettings?.also {
                    languageVersion = it.languageVersion
                    apiVersion = it.apiVersion
                    progressiveMode = it.isProgressiveMode
                    internalArguments = it.enabledLanguageFeatures.mapNotNull {
                        val feature = LanguageFeature.fromString(it) ?: return@mapNotNull null
                        val arg = "-XXLanguage:+$it"
                        ManualLanguageFeatureSetting(feature, LanguageFeature.State.ENABLED, arg)
                    }
                    optIn = it.optInAnnotationsInUse.toTypedArray()
                    pluginOptions = it.compilerPluginArguments
                    pluginClasspaths = it.compilerPluginClasspath.map(File::getPath).toTypedArray()
                    freeArgs = it.freeCompilerArgs.toMutableList()
                }
            }

            val kotlinFacet = ideModule.getOrCreateFacet(modelsProvider, false, GradleConstants.SYSTEM_ID.id)
            kotlinFacet.configureFacet(
                compilerVersion,
                platform,
                modelsProvider,
            )

            ideModule.hasExternalSdkConfiguration = sourceSetNode.data.sdkName != null
            ideModule.isKpmModule = true
            ideModule.refinesFragmentIds = fragmentDataNode.data.refinesFragmentIds.toList()
            applyCompilerArgumentsToFacet(compilerArguments, platform.createArguments(), kotlinFacet, modelsProvider)
            kotlinFacet.noVersionAutoAdvance()
            return kotlinFacet
        }
    }
}