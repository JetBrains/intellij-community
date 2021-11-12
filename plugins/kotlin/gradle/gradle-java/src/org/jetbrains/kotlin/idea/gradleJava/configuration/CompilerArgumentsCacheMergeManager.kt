// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinIdeaProjectData
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File

abstract class CompilerArgumentsCacheMergeManager {
    protected abstract val logger: Logger
    protected abstract fun doCollectCacheAware(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext): CompilerArgumentsCacheAware?
    fun mergeCache(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>, resolverCtx: ProjectResolverContext) {
        val cachePart = doCollectCacheAware(gradleModule, resolverCtx) ?: return
        val projectPath = gradleModule.gradleProject.projectDirectory.absolutePath

        val mainProjectDataNode = if (!projectPath.endsWith("buildSrc"))
            projectDataNode.also { projectDataNodeByPath[projectPath] = it }
        else {
            val directoryPath = projectPath.substringBeforeLast("${File.separator}buildSrc")
            projectDataNodeByPath.entries.first { it.key.startsWith(directoryPath) }.value
        }
        val kotlinIdeaProjectData = ExternalSystemApiUtil.find(mainProjectDataNode, KotlinIdeaProjectData.KEY)?.data
            ?: KotlinIdeaProjectData().also {
                mainProjectDataNode.createChild(KotlinIdeaProjectData.KEY, it)
            }
        kotlinIdeaProjectData.compilerArgumentsCacheHolder.mergeCacheAware(cachePart)
    }

    companion object {
        private val projectDataNodeByPath: HashMap<String, DataNode<ProjectData>> = hashMapOf()
    }
}

class KotlinCompilerArgumentsCacheMergeManager(override val logger: Logger) : CompilerArgumentsCacheMergeManager() {
    override fun doCollectCacheAware(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext): CompilerArgumentsCacheAware? =
        resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)?.partialCacheAware
}

class KotlinMPPCompilerArgumentsCacheMergeManager(override val logger: Logger) : CompilerArgumentsCacheMergeManager() {
    override fun doCollectCacheAware(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext): CompilerArgumentsCacheAware? =
        resolverCtx.getMppModel(gradleModule)?.partialCacheAware
}
