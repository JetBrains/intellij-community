/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.idea.configuration.findChildModuleById
import org.jetbrains.kotlin.idea.configuration.kotlinNativeHome
import org.jetbrains.kotlin.idea.configuration.multiplatform.ComposableInitializeModuleDataAction
import org.jetbrains.kotlin.idea.configuration.multiplatform.InitializeModuleDataContext
import org.jetbrains.kotlin.idea.configuration.multiplatform.ModuleDataInitializer
import org.jetbrains.kotlin.idea.inspections.gradle.findAll
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.util.*
import java.util.stream.Collectors

class KotlinKPMModuleDataInitializer(private val model: KotlinKPMGradleModel) : ModuleDataInitializer {
    private val initializeModuleDataContext: InitializeModuleDataContext<KotlinFragment> = InitializeModuleDataContext()

    override fun doInitialize(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext
    ) {
        (initializeBasicModuleDataAction
                + createGradleSourceSetData
                + createKpmFragmentData
                + initializeModuleNodeDataAction
                ).doInitialize(gradleModule, mainModuleNode, projectDataNode, resolverCtx)
    }

    private val initializeBasicModuleDataAction = object : ComposableInitializeModuleDataAction {
        override fun doInitialize(
            gradleModule: IdeaModule,
            mainModuleNode: DataNode<ModuleData>,
            projectDataNode: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            initializeModuleDataContext.mainModuleData = mainModuleNode.data
            initializeModuleDataContext.mainModuleConfigPath = initializeModuleDataContext.mainModuleData.linkedExternalProjectPath
            initializeModuleDataContext.mainModuleFileDirectoryPath = initializeModuleDataContext.mainModuleData.moduleFileDirectoryPath
            initializeModuleDataContext.jdkName = gradleModule.jdkNameIfAny

            initializeModuleDataContext.moduleGroup = if (!resolverCtx.isUseQualifiedModuleNames) {
                val gradlePath = gradleModule.gradleProject.path
                val isRootModule = gradlePath.isEmpty() || gradlePath == ":"
                if (isRootModule) {
                    arrayOf(initializeModuleDataContext.mainModuleData.internalName)
                } else {
                    gradlePath.split(":").drop(1).toTypedArray()
                }
            } else null
        }
    }

    private val initializeModuleNodeDataAction = object : ComposableInitializeModuleDataAction {
        override fun doInitialize(
            gradleModule: IdeaModule,
            mainModuleNode: DataNode<ModuleData>,
            projectDataNode: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            val mainModuleData = mainModuleNode.data
            with(projectDataNode.data) {
                if (mainModuleData.linkedExternalProjectPath == linkedExternalProjectPath) {
                    group = mainModuleData.group
                    version = mainModuleData.version
                }
            }
            mainModuleNode.kotlinNativeHome = model.kotlinNativeHome
        }
    }

    private val createGradleSourceSetData = object : ComposableInitializeModuleDataAction {
        override fun doInitialize(
            gradleModule: IdeaModule,
            mainModuleNode: DataNode<ModuleData>,
            projectDataNode: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            val sourceSetMap = projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS)!!
            val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)!!

            val fragmentsByModules = model.kpmModules.associateWith { it.fragments }

            for ((module, fragments) in fragmentsByModules) {
                for (fragment in fragments) {
                    val moduleId = calculateKotlinFragmentModuleId(gradleModule, fragment, resolverCtx)
                    val existingSourceSetDataNode = sourceSetMap[moduleId]?.first

                    val moduleExternalName = calculateFragmentExternalModuleName(gradleModule, fragment)
                    val moduleInternalName = calculateFragmentInternalModuleName(
                        gradleModule,
                        externalProject,
                        fragment,
                        resolverCtx,
                    )

                    val fragmentData = existingSourceSetDataNode?.data ?: GradleSourceSetData(
                        moduleId,
                        moduleExternalName,
                        moduleInternalName,
                        initializeModuleDataContext.mainModuleFileDirectoryPath,
                        initializeModuleDataContext.mainModuleConfigPath
                    ).also {
                        it.group = externalProject.group
                        it.version = externalProject.version

                        when (module.moduleIdentifier.moduleClassifier) {
                            KotlinKPMModule.MAIN_MODULE_NAME -> {
                                it.publication = ProjectId(externalProject.group, externalProject.name, externalProject.version)
                            }

                            KotlinKPMModule.TEST_MODULE_NAME -> {
                                it.productionModuleId = moduleInternalName
                            }
                        }

                        it.ideModuleGroup = initializeModuleDataContext.moduleGroup
                        it.sdkName = initializeModuleDataContext.jdkName
                    }

                    if (existingSourceSetDataNode == null) {
                        val fragmentDataNode = mainModuleNode.createChild(GradleSourceSetData.KEY, fragmentData)
                        sourceSetMap[moduleId] = com.intellij.openapi.util.Pair(
                            fragmentDataNode,
                            createExternalSourceSet(fragment, fragmentData)
                        )
                    }
                }
            }
        }

        private fun calculateFragmentExternalModuleName(gradleModule: IdeaModule, fragment: KotlinFragment): String =
            "${gradleModule.name}:${fragment.moduleIdentifier.moduleClassifier ?: KotlinKPMModule.MAIN_MODULE_NAME}.${fragment.fragmentName}"

        private fun calculateFragmentInternalModuleName(
            gradleModule: IdeaModule,
            externalProject: ExternalProject,
            fragment: KotlinFragment,
            resolverCtx: ProjectResolverContext,
        ): String {
            val delimiter: String
            val moduleName = StringBuilder()

            val buildSrcGroup = resolverCtx.buildSrcGroup
            if (resolverCtx.isUseQualifiedModuleNames) {
                delimiter = "."
                if (StringUtil.isNotEmpty(buildSrcGroup)) {
                    moduleName.append(buildSrcGroup).append(delimiter)
                }
                moduleName.append(gradlePathToQualifiedName(gradleModule.project.name, externalProject.qName))
            } else {
                delimiter = "_"
                if (StringUtil.isNotEmpty(buildSrcGroup)) {
                    moduleName.append(buildSrcGroup).append(delimiter)
                }
                moduleName.append(gradleModule.name)
            }
            moduleName.append(delimiter)
            moduleName.append("${fragment.moduleIdentifier.moduleClassifier ?: KotlinKPMModule.MAIN_MODULE_NAME}.${fragment.fragmentName}")
            return PathUtilRt.suggestFileName(moduleName.toString(), true, false)
        }

        private fun gradlePathToQualifiedName(
            rootName: String,
            gradlePath: String
        ): String = ((if (gradlePath.startsWith(":")) "$rootName." else "")
                + Arrays.stream(gradlePath.split(":".toRegex()).toTypedArray())
            .filter { s: String -> s.isNotEmpty() }
            .collect(Collectors.joining(".")))

        private fun createExternalSourceSet(
            fragment: KotlinFragment,
            gradleSourceSetData: GradleSourceSetData,
        ): ExternalSourceSet {
            return DefaultExternalSourceSet().also { sourceSet ->
                sourceSet.name = fragment.fragmentName
                sourceSet.targetCompatibility = gradleSourceSetData.targetCompatibility
                //TODO compute it properly (if required)
                sourceSet.dependencies += emptyList<ExternalDependency>()

                sourceSet.setSources(linkedMapOf(
                    fragment.computeSourceType() to DefaultExternalSourceDirectorySet().also { dirSet ->
                        dirSet.srcDirs = fragment.sourceDirs
                    },
                    fragment.computeResourceType() to DefaultExternalSourceDirectorySet().also { dirSet ->
                        dirSet.srcDirs = fragment.resourceDirs
                    }
                ).toMap())
            }
        }

    }

    private val createKpmFragmentData = object : ComposableInitializeModuleDataAction {
        override fun doInitialize(
            gradleModule: IdeaModule,
            mainModuleNode: DataNode<ModuleData>,
            projectDataNode: DataNode<ProjectData>,
            resolverCtx: ProjectResolverContext
        ) {
            model.kpmModules.flatMap { it.fragments }.forEach { fragment ->
                val moduleId = calculateKotlinFragmentModuleId(gradleModule, fragment, resolverCtx)
                val fragmentGradleSourceSetDataNode = mainModuleNode.findChildModuleById(moduleId)
                    ?: error("Cannot find GradleSourceSetData node for fragment '$moduleId'")

                if (fragmentGradleSourceSetDataNode.findAll(KotlinFragmentData.KEY).isNotEmpty()) return@forEach
                val refinesFragmentsIds = fragment.directRefinesFragments.map {
                    calculateKotlinFragmentModuleId(gradleModule, it, resolverCtx)
                }

                KotlinFragmentData(moduleId).apply {
                    if (fragment is KotlinVariant) {
                        platform = fragment.variantAttributes.values.mapNotNull { KotlinPlatform.byId(it) }.singleOrNull() ?: platform
                    }
                    refinesFragmentIds.addAll(refinesFragmentsIds)
                    resolvedFragmentDependencies.addAll(fragment.resolvedDependencies)
                    languageSettings = fragment.languageSettings
                    fragmentGradleSourceSetDataNode.createChild(KotlinFragmentData.KEY, this)
                }
            }
        }
    }


    companion object {
        private val IdeaModule.jdkNameIfAny
            get() = try {
                jdkName
            } catch (e: UnsupportedMethodException) {
                null
            }
    }
}