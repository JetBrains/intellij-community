// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinOutputPathsData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.resourceType
import org.jetbrains.kotlin.idea.gradleJava.configuration.sourceType
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

internal fun KotlinMppGradleProjectResolver.Context.populateModuleCompileOutputSettings() {
    val ideaOutDir = File(moduleDataNode.data.linkedExternalProjectPath, "out")
    val moduleOutputsMap = projectDataNode.getUserData(GradleProjectResolver.MODULES_OUTPUTS)!!
    val outputDirs = HashSet<String>()
    KotlinMppGradleProjectResolver.getCompilations(gradleModule, mppModel, moduleDataNode, resolverCtx)
        .filterNot { (_, compilation) -> shouldDelegateToOtherPlugin(compilation) }
        .forEach { (dataNode, compilation) ->
            var gradleOutputMap = dataNode.getUserData(GradleProjectResolver.GRADLE_OUTPUTS)
            if (gradleOutputMap == null) {
                gradleOutputMap = MultiMap.create()
                dataNode.putUserData(GradleProjectResolver.GRADLE_OUTPUTS, gradleOutputMap)
            }

            val moduleData = dataNode.data

            with(compilation.output) {
                effectiveClassesDir?.let {
                    moduleData.isInheritProjectCompileOutputPath = false
                    moduleData.setCompileOutputPath(compilation.sourceType, it.absolutePath)
                    for (gradleOutputDir in classesDirs) {
                        recordOutputDir(gradleOutputDir, it, compilation.sourceType, moduleData, moduleOutputsMap, gradleOutputMap)
                    }
                }
                resourcesDir?.let {
                    moduleData.setCompileOutputPath(compilation.resourceType, it.absolutePath)
                    recordOutputDir(it, it, compilation.resourceType, moduleData, moduleOutputsMap, gradleOutputMap)
                }
            }

            dataNode.createChild(KotlinOutputPathsData.KEY, KotlinOutputPathsData(gradleOutputMap.copy()))
        }
    if (outputDirs.any { FileUtil.isAncestor(ideaOutDir, File(it), false) }) {
        excludeOutDir(moduleDataNode, ideaOutDir)
    }
}

private fun recordOutputDir(
    gradleOutputDir: File,
    effectiveOutputDir: File,
    sourceType: ExternalSystemSourceType,
    moduleData: GradleSourceSetData,
    moduleOutputsMap: MutableMap<String, Pair<String, ExternalSystemSourceType>>,
    gradleOutputMap: MultiMap<ExternalSystemSourceType, String>
) {
    val gradleOutputPath = ExternalSystemApiUtil.toCanonicalPath(gradleOutputDir.absolutePath)
    gradleOutputMap.putValue(sourceType, gradleOutputPath)
    if (gradleOutputDir.path != effectiveOutputDir.path) {
        moduleOutputsMap[gradleOutputPath] = Pair(moduleData.id, sourceType)
    }
}

private fun excludeOutDir(moduleDataNode: DataNode<ModuleData>, ideaOutDir: File) {
    val contentRootDataDataNode = ExternalSystemApiUtil.find(moduleDataNode, ProjectKeys.CONTENT_ROOT)

    val excludedContentRootData: ContentRootData
    if (contentRootDataDataNode == null || !FileUtil.isAncestor(File(contentRootDataDataNode.data.rootPath), ideaOutDir, false)) {
        excludedContentRootData = ContentRootData(GradleConstants.SYSTEM_ID, ideaOutDir.absolutePath)
        moduleDataNode.createChild(ProjectKeys.CONTENT_ROOT, excludedContentRootData)
    } else {
        excludedContentRootData = contentRootDataDataNode.data
    }

    excludedContentRootData.storePath(ExternalSystemSourceType.EXCLUDED, ideaOutDir.absolutePath)
}
