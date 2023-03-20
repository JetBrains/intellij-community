// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectId
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmFragment
import org.jetbrains.kotlin.gradle.idea.kpm.name
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ModuleDataInitializer
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.util.*
import java.util.stream.Collectors

@Order(ExternalSystemConstants.UNORDERED + 1)
class KotlinGradleSourceSetDataInitializer : ModuleDataInitializer {
    override fun initialize(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext,
        initializerContext: ModuleDataInitializer.Context
    ) {
        val sourceSetMap = projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS)!!
        val externalProject = resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)!!

        val fragmentsByModules = initializerContext.model?.modules?.associateWith { it.fragments } ?: return

        for ((module, fragments) in fragmentsByModules) {
            for (fragment in fragments) {
                val fragmentModuleId = calculateKotlinFragmentModuleId(gradleModule, fragment.coordinates, resolverCtx)
                val existingSourceSetDataNode = sourceSetMap[fragmentModuleId]?.first

                val moduleExternalName = calculateFragmentExternalModuleName(gradleModule, fragment)
                val moduleInternalName = calculateFragmentInternalModuleName(
                    gradleModule,
                    externalProject,
                    fragment,
                    resolverCtx,
                )

                val fragmentData = existingSourceSetDataNode?.data ?: GradleSourceSetData(
                    fragmentModuleId,
                    moduleExternalName,
                    moduleInternalName,
                    initializerContext.mainModuleFileDirectoryPath.orEmpty(),
                    initializerContext.mainModuleConfigPath.orEmpty()
                ).also {
                    it.group = externalProject.group
                    it.version = externalProject.version

                    when (module.coordinates.moduleName) {
                        "main" -> {
                            it.publication = ProjectId(externalProject.group, externalProject.name, externalProject.version)
                        }

                        "test" -> {
                            it.productionModuleId = moduleInternalName
                        }
                    }

                    it.ideModuleGroup = initializerContext.moduleGroup
                    it.sdkName = initializerContext.jdkName
                }

                if (existingSourceSetDataNode == null) {
                    val fragmentDataNode = mainModuleNode.createChild(GradleSourceSetData.KEY, fragmentData)
                    sourceSetMap[fragmentModuleId] = com.intellij.openapi.util.Pair(
                        fragmentDataNode,
                        createExternalSourceSet(fragment, fragmentData)
                    )
                }
            }
        }
    }

    companion object {
        //TODO should it be visible for anyone outside initializer? Maybe introduce services for naming/routing fragments?
        private fun calculateFragmentExternalModuleName(gradleModule: IdeaModule, fragment: IdeaKpmFragment): String =
            "${gradleModule.name}:${fragment.coordinates.module.moduleName}.${fragment.name}"

        private fun calculateFragmentInternalModuleName(
            gradleModule: IdeaModule,
            externalProject: ExternalProject,
            fragment: IdeaKpmFragment,
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
            moduleName.append("${fragment.coordinates.module.moduleName}.${fragment.name}")
            return PathUtilRt.suggestFileName(moduleName.toString(), true, false)
        }

        private fun gradlePathToQualifiedName(
            rootName: String,
            gradlePath: String
        ): String = ((if (gradlePath.startsWith(":")) "$rootName." else "")
                + Arrays.stream(gradlePath.split(":".toRegex()).toTypedArray())
            .filter { s: String -> s.isNotEmpty() }
            .collect(Collectors.joining(".")))
    }

    private fun createExternalSourceSet(
        fragment: IdeaKpmFragment,
        gradleSourceSetData: GradleSourceSetData,
    ): ExternalSourceSet {
        return DefaultExternalSourceSet().also { sourceSet ->
            sourceSet.name = fragment.name
            sourceSet.targetCompatibility = gradleSourceSetData.targetCompatibility
            //TODO compute it properly (if required)
            sourceSet.dependencies += emptyList<ExternalDependency>()

            sourceSet.setSources(linkedMapOf(
                fragment.computeSourceType() to DefaultExternalSourceDirectorySet().also { dirSet ->
                    dirSet.srcDirs = fragment.sourceDirs.toSet()
                },
                fragment.computeResourceType() to DefaultExternalSourceDirectorySet().also { dirSet ->
                    dirSet.srcDirs = fragment.resourceDirs.toSet()
                }
            ).toMap())
        }
    }
}
