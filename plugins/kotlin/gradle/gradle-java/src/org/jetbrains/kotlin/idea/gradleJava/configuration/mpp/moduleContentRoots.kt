// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.kotlinGradleProjectDataOrFail
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension.Result.Skip
import org.jetbrains.kotlin.idea.gradleJava.configuration.resourceType
import org.jetbrains.kotlin.idea.gradleJava.configuration.sourceType
import org.jetbrains.kotlin.idea.gradleJava.configuration.utils.KotlinModuleUtils
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

internal fun KotlinMppGradleProjectResolver.Context.populateContentRoots(
) {
    val extensionInstance = KotlinMppGradleProjectResolverExtension.buildInstance()

    val sourceSetToPackagePrefix = mppModel.targets.flatMap { it.compilations }
        .flatMap { compilation ->
            compilation.declaredSourceSets.map { sourceSet -> sourceSet.name to compilation.kotlinTaskProperties.packagePrefix }
        }
        .toMap()
    if (resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java) == null) return
    processSourceSets(gradleModule, mppModel, moduleDataNode, resolverCtx) { dataNode, sourceSet ->
        if (dataNode == null || shouldDelegateToOtherPlugin(sourceSet)) return@processSourceSets

        /* Execute all registered extension points and skip population of content roots if instructed by extensions */
        if (extensionInstance.beforePopulateContentRoots(this, dataNode, sourceSet) == Skip) {
            return@processSourceSets
        }

        createContentRootData(
            sourceSet.sourceDirs,
            sourceSet.sourceType,
            sourceSetToPackagePrefix[sourceSet.name],
            dataNode
        )
        createContentRootData(
            sourceSet.resourceDirs,
            sourceSet.resourceType,
            null,
            dataNode
        )

        extensionInstance.afterPopulateContentRoots(this, dataNode, sourceSet)
    }

    for (gradleContentRoot in gradleModule.contentRoots ?: emptySet<IdeaContentRoot?>()) {
        if (gradleContentRoot == null) continue

        val rootDirectory = gradleContentRoot.rootDirectory ?: continue
        val ideContentRoot = ContentRootData(GradleConstants.SYSTEM_ID, rootDirectory.absolutePath).also { ideContentRoot ->
            (gradleContentRoot.excludeDirectories ?: emptySet()).forEach { file ->
                ideContentRoot.storePath(ExternalSystemSourceType.EXCLUDED, file.absolutePath)
            }
        }
        moduleDataNode.createChild(ProjectKeys.CONTENT_ROOT, ideContentRoot)
    }

    val mppModelPureKotlinSourceFolders = mppModel.targets.flatMap { it.compilations }
        .flatMap { it.kotlinTaskProperties.pureKotlinSourceFolders ?: emptyList() }
        .map { it.absolutePath }

    moduleDataNode.kotlinGradleProjectDataOrFail.pureKotlinSourceFolders.addAll(mppModelPureKotlinSourceFolders)
}

private fun processSourceSets(
    gradleModule: IdeaModule,
    mppModel: KotlinMPPGradleModel,
    ideModule: DataNode<ModuleData>,
    resolverCtx: ProjectResolverContext,
    processor: (DataNode<GradleSourceSetData>?, KotlinSourceSet) -> Unit
) {
    val sourceSetsMap = HashMap<String, DataNode<GradleSourceSetData>>()
    for (dataNode in ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)) {
        if (dataNode.kotlinSourceSetData?.sourceSetInfo != null) {
            sourceSetsMap[dataNode.data.id] = dataNode
        }
    }
    for (sourceSet in mppModel.sourceSetsByName.values) {
        val moduleId = KotlinModuleUtils.getKotlinModuleId(gradleModule, sourceSet, resolverCtx)
        val moduleDataNode = sourceSetsMap[moduleId]
        processor(moduleDataNode, sourceSet)
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
        contentRootData.storePath(sourceType, sourceDir.absolutePath, packagePrefix)
        parentNode.createChild(ProjectKeys.CONTENT_ROOT, contentRootData)
    }
}
