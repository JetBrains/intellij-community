// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradle.configuration.utils.UnsafeTestSourceSetHeuristicApi
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

class KotlinJavaMPPSourceSetDataService : AbstractProjectDataService<GradleSourceSetData, Void>() {
    override fun getTargetDataKey() = GradleSourceSetData.KEY

    override fun postProcess(
        toImport: MutableCollection<out DataNode<GradleSourceSetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        val isTestModuleById = toImport.associate { it.data.internalName to (it.kotlinSourceSetData?.sourceSetInfo?.isTestModule ?: false) }
        val testKotlinModules =
            toImport.filter { it.kotlinSourceSetData?.sourceSetInfo?.isTestModule ?: false }.map { modelsProvider.findIdeModule(it.data) }
                .toSet()
        val projectNode = toImport.firstOrNull()?.let { ExternalSystemApiUtil.findParent(it, ProjectKeys.PROJECT) } ?: return
        val targetsByUrl = ExternalSystemApiUtil
            .findAllRecursively(projectNode, KotlinTargetData.KEY)
            .groupBy { targetNode -> targetNode.data.archiveFile?.let { VfsUtil.getUrlForLibraryRoot(it) } }
        for (nodeToImport in toImport) {
            if (nodeToImport.kotlinSourceSetData?.sourceSetInfo != null) continue
            @OptIn(UnsafeTestSourceSetHeuristicApi::class)
            val isTestSourceSet = isTestSourceSet(nodeToImport)
            val moduleData = nodeToImport.data
            val module = modelsProvider.findIdeModule(moduleData) ?: continue
            val rootModel = modelsProvider.getModifiableRootModel(module)

            val moduleEntries = rootModel.orderEntries.filterIsInstance<ModuleOrderEntry>()
            moduleEntries.filter { isTestModuleById[it.moduleName] ?: false }.forEach { moduleOrderEntry ->
                moduleOrderEntry.isProductionOnTestDependency = true
            }
            val libraryEntries = rootModel.orderEntries.filterIsInstance<LibraryOrderEntry>()
            libraryEntries.forEach { libraryEntry ->
                //TODO check that this code is nessecary any more. In general case all dependencies on MPP are already resolved into module dependencies
                val library = libraryEntry.library ?: return@forEach
                val libraryModel = modelsProvider.getModifiableLibraryModel(library)
                val classesUrl = libraryModel.getUrls(OrderRootType.CLASSES).singleOrNull() ?: return@forEach
                val targetNode = targetsByUrl[classesUrl]?.singleOrNull() ?: return@forEach
                val groupingModuleNode = ExternalSystemApiUtil.findParent(targetNode, ProjectKeys.MODULE) ?: return@forEach
                val compilationNodes = ExternalSystemApiUtil
                    .getChildren(groupingModuleNode, GradleSourceSetData.KEY)
                    .filter { it.data.id in targetNode.data.moduleIds }
                for (compilationNode in compilationNodes) {
                    val compilationModule = modelsProvider.findIdeModule(compilationNode.data) ?: continue
                    val compilationInfo = compilationNode.kotlinSourceSetData?.sourceSetInfo ?: continue
                    if (compilationInfo.isTestModule) continue
                    val compilationRootModel = modelsProvider.getModifiableRootModel(compilationModule)
                    addModuleDependencyIfNeeded(
                        rootModel,
                        compilationModule,
                        isTestSourceSet,
                        compilationNode.kotlinSourceSetData?.sourceSetInfo?.isTestModule ?: false
                    )
                    compilationRootModel.getModuleDependencies(isTestSourceSet).forEach { transitiveDependee ->
                        addModuleDependencyIfNeeded(
                            rootModel,
                            transitiveDependee,
                            isTestSourceSet,
                            testKotlinModules.contains(transitiveDependee)
                        )
                    }
                }
                rootModel.removeOrderEntry(libraryEntry)
            }
        }
    }

    @UnsafeTestSourceSetHeuristicApi
    private fun isTestSourceSet(node: DataNode<GradleSourceSetData>): Boolean {
        return node.data.id.endsWith(":test") || node.data.id.endsWith(":unitTest") || node.data.id.endsWith(":androidTest")
    }
}
