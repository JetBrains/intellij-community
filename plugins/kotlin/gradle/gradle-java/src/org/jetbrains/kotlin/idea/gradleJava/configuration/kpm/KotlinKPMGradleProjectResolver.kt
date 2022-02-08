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
import org.jetbrains.kotlin.idea.PlatformVersion
import org.jetbrains.kotlin.idea.configuration.multiplatform.KotlinMultiplatformNativeDebugSuggester
import org.jetbrains.kotlin.idea.gradle.configuration.ResolveModulesPerSourceSetInMppBuildIssue
import org.jetbrains.kotlin.idea.gradle.configuration.buildClasspathData
import org.jetbrains.kotlin.idea.gradle.configuration.findChildModuleById
import org.jetbrains.kotlin.idea.gradle.ui.notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded
import org.jetbrains.kotlin.idea.gradleTooling.*
import org.jetbrains.kotlin.idea.projectModel.KotlinFragment
import org.jetbrains.kotlin.idea.roots.findAll
import org.jetbrains.plugins.gradle.model.data.BuildScriptClasspathData
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.lang.reflect.Proxy

@Order(ExternalSystemConstants.UNORDERED + 1)
open class KotlinKPMGradleProjectResolver : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses(): Set<Class<out Any>> = setOf(KotlinKPMGradleModel::class.java)
    override fun getToolingExtensionsClasses(): Set<Class<out Any>> = setOf(KotlinKPMGradleModelBuilder::class.java, Unit::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (ExternalSystemApiUtil.find(ideModule, BuildScriptClasspathData.KEY) == null) {
            val buildScriptClasspathData = buildClasspathData(gradleModule, resolverCtx)
            ideModule.createChild(BuildScriptClasspathData.KEY, buildScriptClasspathData)
        }

        super.populateModuleExtraModels(gradleModule, ideModule)
    }

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        return super.createModule(gradleModule, projectDataNode)?.also {
            val model = getModelOrNull(gradleModule) ?: return@also
            KotlinKPMModuleDataInitializer(model).doInitialize(gradleModule, it, projectDataNode, resolverCtx)
        }
    }

    override fun populateModuleContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        if (!modelExists(gradleModule)) {
            return super.populateModuleContentRoots(gradleModule, ideModule)
        }
        nativeDebugSuggester.suggestNativeDebug(getModelOrNull(gradleModule), resolverCtx)

        if (!resolverCtx.isResolveModulePerSourceSet && !PlatformVersion.isAndroidStudio() && !PlatformUtils.isMobileIde() &&
            !PlatformUtils.isAppCode()
        ) {
            notifyLegacyIsResolveModulePerSourceSetSettingIfNeeded(resolverCtx.projectPath)
            resolverCtx.report(MessageEvent.Kind.WARNING, ResolveModulesPerSourceSetInMppBuildIssue())
        }

        val sourceSetsMap = ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY).filter {
            ExternalSystemApiUtil.find(it, KotlinFragmentData.KEY) != null
        }.associateBy { it.data.id }
        val model = resolverCtx.getKpmModel(gradleModule)!!

        for (fragment in model.kpmModules.flatMap { it.fragments }) {
            val moduleId = calculateKotlinFragmentModuleId(gradleModule, fragment, resolverCtx)
            val moduleDataNode = sourceSetsMap[moduleId]
            if (moduleDataNode == null) continue

            createContentRootData(
                extractSourceDirs(fragment),
                fragment.computeSourceType(),
                extractPackagePrefix(fragment),
                moduleDataNode
            )
            createContentRootData(
                extractResourceDirs(fragment),
                fragment.computeResourceType(),
                null,
                moduleDataNode
            )
        }

        gradleModule.contentRoots.mapNotNull {
            val gradleContentRoot = it ?: return@mapNotNull null
            val rootDirectory = it.rootDirectory ?: return@mapNotNull null
            ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.absolutePath).also { ideContentRoot ->
                (gradleContentRoot.excludeDirectories ?: emptySet()).forEach { file ->
                    ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.absolutePath)
                }
            }
        }.forEach { ideModule.createChild(ProjectKeys.CONTENT_ROOT, it) }
    }

    override fun populateModuleDependencies(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, ideProject: DataNode<ProjectData>) {
        if (!modelExists(gradleModule)) {
            return super.populateModuleDependencies(gradleModule, ideModule, ideProject)
        }
        populateDependenciesByFragmentData(ideModule, ideProject)
    }

    private fun modelExists(gradleModule: IdeaModule): Boolean = getModelOrNull(gradleModule) != null
    private fun getModelOrNull(gradleModule: IdeaModule): KotlinKPMGradleModel? = resolverCtx.getKpmModel(gradleModule)

    companion object {
        private val nativeDebugSuggester = object : KotlinMultiplatformNativeDebugSuggester<KotlinKPMGradleModel>() {
            override fun hasKotlinNativeHome(model: KotlinKPMGradleModel?): Boolean = model?.kotlinNativeHome?.isNotEmpty() ?: false
        }

        fun ProjectResolverContext.getKpmModel(gradleModule: IdeaModule): KotlinKPMGradleModel? {
            return when (val kpmModel = this.getExtraProject(gradleModule, KotlinKPMGradleModel::class.java)) {
                is Proxy? -> kpmModel?.let { kotlinKpmModel ->
                    KotlinKPMGradleModelImpl(
                        kpmModules = kotlinKpmModel.kpmModules,
                        settings = kotlinKpmModel.settings,
                        kotlinNativeHome = kotlinKpmModel.kotlinNativeHome,
                    )
                }
                else -> kpmModel
            }
        }


        private fun createContentRootData(
            sourceDirs: Set<File>,
            sourceType: ExternalSystemSourceType,
            packagePrefix: String?,
            parentNode: DataNode<*>
        ) {
            for (sourceDir in sourceDirs) {
                val contentRootData = ContentRootData(GradleConstants.SYSTEM_ID, sourceDir.absolutePath)
                packagePrefix?.also {
                    contentRootData.storePath(sourceType, sourceDir.absolutePath, it)
                } ?: contentRootData.storePath(sourceType, sourceDir.absolutePath)
                parentNode.createChild(ProjectKeys.CONTENT_ROOT, contentRootData)
            }
        }

        //TODO check this
        private fun extractPackagePrefix(fragment: KotlinFragment): String? = null
        private fun extractSourceDirs(fragment: KotlinFragment): Set<File> = fragment.sourceDirs
        private fun extractResourceDirs(fragment: KotlinFragment): Set<File> = fragment.resourceDirs
        private fun extractContentRootSources(multiplatformModel: KotlinKPMGradleModel): Collection<KotlinFragment> =
            multiplatformModel.kpmModules.flatMap { it.fragments }

        //TODO replace with proper implementation, like with KotlinTaskProperties
        private fun extractPureKotlinSourceFolders(fragment: KotlinFragment): Collection<File> = fragment.sourceDirs.toList()

        //TODO Unite with KotlinGradleProjectResolverExtension.getSourceSetName
        private val KotlinKPMGradleModel.pureKotlinSourceFolders: Collection<String>
            get() = extractContentRootSources(this).flatMap { extractPureKotlinSourceFolders(it) }.map { it.absolutePath }

        private val DataNode<out ModuleData>.sourceSetName
            get() = (data as? GradleSourceSetData)?.id?.substringAfterLast(':')

        //TODO Unite with KotlinGradleProjectResolverExtension.addDependency
        private fun addModuleDependency(
            dependentModule: DataNode<out ModuleData>,
            dependencyModule: DataNode<out ModuleData>,
            isPropagated: Boolean = false
        ) {
            val moduleDependencyData = ModuleDependencyData(dependentModule.data, dependencyModule.data)
            //TODO Replace with proper scope from dependency
            moduleDependencyData.scope = if (isPropagated) DependencyScope.PROVIDED else DependencyScope.COMPILE
            moduleDependencyData.isExported = false
            moduleDependencyData.isProductionOnTestDependency = dependencyModule.sourceSetName == "test"
            dependentModule.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData)
        }

        private fun populateDependenciesByFragmentData(
            ideModule: DataNode<ModuleData>,
            ideProject: DataNode<ProjectData>
        ) {
            val allModules = ExternalSystemApiUtil.findAll(ideProject, ProjectKeys.MODULE)

            val sourceSetDataWithFragmentData = ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)
                .mapNotNull { ExternalSystemApiUtil.find(it, KotlinFragmentData.KEY)?.data?.let { fragmentData -> it to fragmentData } }
                .sortedBy { it.second.refinesFragmentIds.size }
            for ((fragmentSourceSetNode, kpmFragmentData) in sourceSetDataWithFragmentData) {

                val refinesFragmentNodes = kpmFragmentData.refinesFragmentIds.mapNotNull { ideModule.findChildModuleById(it) }
                refinesFragmentNodes.forEach {
                    addModuleDependency(fragmentSourceSetNode, it)
                }

                //TODO asymptotic
                kpmFragmentData.resolvedFragmentDependencies.forEach { dependency ->
                    when (dependency) {
                        is KotlinFragmentResolvedSourceDependency -> {
                            val resolvedLocalFragmentSourceSetsDataNode = allModules.mapNotNull { node ->
                                node.findChildModuleById(dependency.dependencyIdentifier)
                            }.single()
                            addModuleDependency(fragmentSourceSetNode, resolvedLocalFragmentSourceSetsDataNode)
                        }
                        is KotlinFragmentResolvedBinaryDependency -> populateLibraryDependency(
                            fragmentSourceSetNode,
                            ideProject,
                            dependency
                        )
                    }
                }
                //Propagation of ModuleDependencyData through refines edges
                val moduleDependencies = ExternalSystemApiUtil.findAll(fragmentSourceSetNode, ProjectKeys.MODULE_DEPENDENCY)
                    .map { it.data.target }
                val moduleDependenciesToBePropagated = refinesFragmentNodes.flatMap { refinesModuleDep ->
                    ExternalSystemApiUtil.findAll(refinesModuleDep, ProjectKeys.MODULE_DEPENDENCY)
                }.filter { it.data.target !in moduleDependencies }
                    .filterNotNull()
                    .distinct()
                    .map { it.data.target }

                val nodesToBePropagated = moduleDependenciesToBePropagated.mapNotNull {
                    ideModule.findChildModuleById(it.id)
                }
                nodesToBePropagated.forEach { addModuleDependency(fragmentSourceSetNode, it, isPropagated = true) }
            }
        }

        private fun populateLibraryDependency(
            dependentModule: DataNode<out ModuleData>,
            projectDataNode: DataNode<out ProjectData>,
            moduleDependency: KotlinFragmentResolvedBinaryDependency
        ) {
            val existingLibraryNodeWithData = projectDataNode.findAll(ProjectKeys.LIBRARY).find {
                it.data.owner == GradleConstants.SYSTEM_ID && it.data.externalName == moduleDependency.dependencyIdentifier
            }
            val libraryData: LibraryData
            if (existingLibraryNodeWithData != null) {
                libraryData = existingLibraryNodeWithData.data
            } else {
                libraryData = LibraryData(GradleConstants.SYSTEM_ID, moduleDependency.dependencyIdentifier)
                moduleDependency.dependencyContent?.forEach { libraryData.addPath(LibraryPathType.BINARY, it.absolutePath) }
                projectDataNode.createChild(ProjectKeys.LIBRARY, libraryData)
            }

            val libraryDependencyData = LibraryDependencyData(dependentModule.data, libraryData, LibraryLevel.PROJECT)
            dependentModule.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
        }
    }

}