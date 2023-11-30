// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ContentRootsCreator
import org.jetbrains.kotlin.idea.gradleJava.configuration.kpm.KotlinKPMGradleProjectResolver.Companion.getIdeaKpmProject
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

@Order(ExternalSystemConstants.UNORDERED)
class KotlinFragmentContentRootsCreator : ContentRootsCreator {
    override fun populateContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, resolverCtx: ProjectResolverContext) {
        val sourceSetsMap = ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY).filter {
            ExternalSystemApiUtil.find(it, KotlinFragmentData.KEY) != null
        }.associateBy { it.data.id }

        val model = resolverCtx.getIdeaKpmProject(gradleModule)!!

        for (fragment in model.modules.flatMap { it.fragments }) {
            val moduleId = calculateKotlinFragmentModuleId(gradleModule, fragment.coordinates, resolverCtx)
            val moduleDataNode = sourceSetsMap[moduleId] ?: continue

            createContentRootData(
                fragment.sourceDirs.toSet(),
                fragment.computeSourceType(),
                KotlinKPMGradleProjectResolver.extractPackagePrefix(fragment),
                moduleDataNode
            )
            createContentRootData(
                fragment.resourceDirs.toSet(),
                fragment.computeResourceType(),
                null,
                moduleDataNode
            )
        }
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
