// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModelBuilder
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Order(ExternalSystemConstants.UNORDERED + 3)
class KotlinCompilerArgumentsCacheResolver : AbstractProjectResolverExtension() {
    override fun getToolingExtensionsClasses(): Set<Class<out Any>> =
        setOf(KotlinGradleModelBuilder::class.java, KotlinMPPGradleModelBuilder::class.java, Unit::class.java)

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData>? {
        val ktCacheManager = KotlinCompilerArgumentsCacheMergeManager(KT_LOGGER)
        val mppCacheManager = KotlinMPPCompilerArgumentsCacheMergeManager(MPP_LOGGER)
        return super.createModule(gradleModule, projectDataNode).also {
            ktCacheManager.mergeCache(gradleModule, projectDataNode, resolverCtx)
            mppCacheManager.mergeCache(gradleModule, projectDataNode, resolverCtx)
        }
    }

    companion object {
        private val KT_LOGGER = Logger.getInstance(KotlinGradleProjectResolverExtension::class.java)
        private val MPP_LOGGER = Logger.getInstance(KotlinMPPGradleProjectResolver::class.java)
    }
}
