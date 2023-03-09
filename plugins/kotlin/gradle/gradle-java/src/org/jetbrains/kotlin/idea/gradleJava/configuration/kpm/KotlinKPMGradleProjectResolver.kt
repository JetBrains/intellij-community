// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.roots.DependencyScope
import com.intellij.util.PlatformUtils
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.kpm.*
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.configuration.multiplatform.KotlinMultiplatformNativeDebugSuggester
import org.jetbrains.kotlin.idea.gradle.configuration.ResolveModulesPerSourceSetInMppBuildIssue
import org.jetbrains.kotlin.idea.gradle.configuration.buildClasspathData
import org.jetbrains.kotlin.idea.gradle.configuration.findChildModuleById
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ContentRootsCreator
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ModuleDataInitializer
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.tooling.core.Extras
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

@Order(ExternalSystemConstants.UNORDERED + 1)
open class KotlinKPMGradleProjectResolver : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> =
        throw UnsupportedOperationException("Use getModelProvider() instead!")

    override fun getModelProvider(): ProjectImportModelProvider? = IdeaKpmProjectProvider

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> = setOf(
        IdeaKpmProject::class.java,  // representative of kotlin-gradle-plugin-idea
        Extras::class.java, // representative of kotlin-tooling-core
        Unit::class.java // representative of kotlin-stdlib
    )

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (ExternalSystemApiUtil.find(ideModule, BuildScriptClasspathData.KEY) == null) {
            val buildScriptClasspathData = buildClasspathData(gradleModule, resolverCtx)
            ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData)
        }

        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        return super.createModule(gradleModule, projectDataNode)?.also { mainModuleNode ->
            val initializerContext = ModuleDataInitializer.Context.EMPTY
            ModuleDataInitializer.EP_NAME.extensions.forEach { moduleDataInitializer ->
                moduleDataInitializer.initialize(gradleModule, mainModuleNode, projectDataNode, resolverCtx, initializerContext)
            }
            suggestNativeDebug(gradleModule, resolverCtx)
        }
    }

    override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (!modelExists(gradleModule)) {
            return super.populateModuleContentRoots(gradleModule, ideModule)
        }
        ContentRootsCreator.EP_NAME.extensions.forEach { contentRootsCreator ->
            contentRootsCreator.populateContentRoots(gradleModule, ideModule, resolverCtx)
        }
    }

    override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {
        if (!modelExists(gradleModule)) {
            return super.populateModuleDependencies(gradleModule, ideModule, ideProject)
        }
        populateDependenciesByFragmentData(gradleModule, ideModule, ideProject, resolverCtx)
    }

    private fun modelExists(gradleModule: IdeaModule): Boolean = resolverCtx.getIdeaKpmProject(gradleModule) != null

    companion object {
        private val nativeDebugSuggester = object : KotlinMultiplatformNativeDebugSuggester<IdeaKpmProject>() {
            override fun hasKotlinNativeHome(model: IdeaKpmProject?): Boolean = model?.kotlinNativeHome?.exists() ?: false
        }

        internal fun ProjectResolverContext.getIdeaKpmProject(gradleModule: IdeaModule): IdeaKpmProject? {
            return this.getExtraProject(gradleModule, IdeaKpmProjectContainer::class.java)?.instanceOrNull
        }

        private fun suggestNativeDebug(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext) {
            nativeDebugSuggester.suggestNativeDebug(resolverCtx.getIdeaKpmProject(gradleModule), resolverCtx)

            if (!resolverCtx.isResolveModulePerSourceSet && !KotlinPlatformUtils.isAndroidStudio && !PlatformUtils.isMobileIde() &&
                !PlatformUtils.isAppCode()
            ) {
                notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(resolverCtx.projectPath)
                resolverCtx.report(MessageEvent.Kind.WARNING, ResolveModulesPerSourceSetInMppBuildIssue())
            }
        }

        //TODO check this
        internal fun extractPackagePrefix(fragment: IdeaKpmFragment): String? = null
        private fun extractContentRootSources(model: IdeaKpmProject): Collection<IdeaKpmFragment> =
            model.modules.flatMap { it.fragments }

        //TODO replace with proper implementation, like with KotlinTaskProperties
        private fun extractPureKotlinSourceFolders(fragment: IdeaKpmFragment): Collection<File> = fragment.sourceDirs

        //TODO Unite with KotlinGradleProjectResolverExtension.getSourceSetName
        internal val IdeaKpmProject.pureKotlinSourceFolders: Collection<String>
            get() = extractContentRootSources(this).flatMap { extractPureKotlinSourceFolders(it) }.map { it.absolutePath }

        internal val DataNode<out ModuleData>.sourceSetName
            get() = (data as? GradleSourceSetData)?.id?.substringAfterLast(':')

        //TODO Unite with KotlinGradleProjectResolverExtension.addDependency
        private fun addModuleDependency(
            dependentModule: DataNode<out ModuleData>,
            dependencyModule: DataNode<out ModuleData>,
        ) {
            val moduleDependencyData = ModuleDependencyData(dependentModule.data, dependencyModule.data)
            //TODO Replace with proper scope from dependency
            moduleDependencyData.scope = DependencyScope.COMPILE
            moduleDependencyData.isExported = false
            moduleDependencyData.isProductionOnTestDependency = dependencyModule.sourceSetName == "test"
            dependentModule.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
        }

        private fun populateDependenciesByFragmentData(
            gradleModule: IdeaModule,
            ideModule: DataNode<ModuleData>,
            ideProject: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            val allGradleModules = gradleModule.project.modules
            val allModuleDataNodes = ExternalSystemApiUtil.findAll(ideProject, ProjectKeys.MODULE)
            val allFragmentModulesById = allModuleDataNodes.flatMap { ExternalSystemApiUtil.findAll(it, GradleSourceSetData.KEY) }
                .associateBy { it.data.id }

            val sourceSetDataWithFragmentData = ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)
                .mapNotNull { ExternalSystemApiUtil.find(it, KotlinFragmentData.KEY)?.data?.let { fragmentData -> it to fragmentData } }
                .sortedBy { it.second.refinesFragmentIds.size }
            for ((fragmentSourceSetNode, kpmFragmentData) in sourceSetDataWithFragmentData) {

                val refinesFragmentNodes = kpmFragmentData.refinesFragmentIds.mapNotNull { ideModule.findChildModuleById(it) }
                refinesFragmentNodes.forEach {
                    addModuleDependency(fragmentSourceSetNode, it)
                }

                val sourceDependencyIds = kpmFragmentData.fragmentDependencies
                    .filterIsInstance<IdeaKpmFragmentDependency>()
                    .mapNotNull { fragmentDependency ->
                        val foundGradleModule = allGradleModules.singleOrNull { dependencyGradleModule ->
                            dependencyGradleModule.name == fragmentDependency.coordinates.module.projectName
                        } ?: return@mapNotNull null // Probably it's worth to log
                        fragmentDependency to foundGradleModule
                    }
                    .map { (dependency, module) -> calculateKotlinFragmentModuleId(module, dependency.coordinates, resolverCtx) }

                val dependencyModuleNodes = sourceDependencyIds.mapNotNull { allFragmentModulesById[it] }
                dependencyModuleNodes.forEach { addModuleDependency(fragmentSourceSetNode, it) }

                val groupedBinaryDependencies = kpmFragmentData.fragmentDependencies
                    .filterIsInstance<IdeaKpmResolvedBinaryDependency>()
                    .groupBy { it.coordinates.toString() }
                    .map { (coordinates, binariesWithType) ->
                        GroupedLibraryDependency(coordinates, binariesWithType.map { it.binaryFile to it.binaryType.toBinaryType() })
                    }

                groupedBinaryDependencies.forEach {
                    populateLibraryDependency(fragmentSourceSetNode, ideProject, it)
                }
            }
        }

        private fun String.toBinaryType(): LibraryPathType = when (this) {
            IdeaKpmDependency.CLASSPATH_BINARY_TYPE -> LibraryPathType.BINARY
            IdeaKpmDependency.SOURCES_BINARY_TYPE -> LibraryPathType.SOURCE
            IdeaKpmDependency.DOCUMENTATION_BINARY_TYPE -> LibraryPathType.DOC
            else -> LibraryPathType.EXCLUDED
        }

        private data class GroupedLibraryDependency(
            val coordinates: String?,
            val binariesWithType: Collection<Pair<File, LibraryPathType>>
        )

        private fun populateLibraryDependency(
            dependentModule: DataNode<out ModuleData>,
            projectDataNode: DataNode<out ProjectData>,
            binaryDependency: GroupedLibraryDependency
        ) {
            val coordinates = binaryDependency.coordinates ?: return
            val existingLibraryNodeWithData = projectDataNode.findAll(ProjectKeys.LIBRARY).find {
                it.data.owner == GradleConstants.SYSTEM_ID && it.data.externalName == coordinates
            }
            val libraryData: LibraryData
            if (existingLibraryNodeWithData != null) {
                libraryData = existingLibraryNodeWithData.data
            } else {
                libraryData = LibraryData(GradleConstants.SYSTEM_ID, coordinates).apply {
                    binaryDependency.binariesWithType.forEach { (binaryFile, pathType) ->
                        addPath(pathType, binaryFile.absolutePath)
                    }
                }
                projectDataNode.createChild(ProjectKeys.LIBRARY, libraryData)
            }
            val libraryDependencyData = LibraryDependencyData(dependentModule.data, libraryData, LibraryLevel.PROJECT)
            dependentModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
        }
    }

}
