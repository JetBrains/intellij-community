// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ContentRootsCreator
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.util.GradleConstants

@Order(ExternalSystemConstants.UNORDERED)
class KotlinGradleModuleContentRootsCreator : ContentRootsCreator {
    override fun populateContentRoots(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>, resolverCtx: ProjectResolverContext) {
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
}