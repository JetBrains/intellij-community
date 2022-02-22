// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheHolder
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

abstract class CompilerArgumentsCacheMergeManager {
    protected abstract fun doCollectCacheAware(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext): CompilerArgumentsCacheAware?
    fun mergeCache(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext) {
        val cachePart = doCollectCacheAware(gradleModule, resolverCtx) ?: return
        compilerArgumentsCacheHolder.mergeCacheAware(cachePart)
    }

    companion object {
        val compilerArgumentsCacheHolder: CompilerArgumentsCacheHolder = CompilerArgumentsCacheHolder()
    }
}

object KotlinCompilerArgumentsCacheMergeManager : CompilerArgumentsCacheMergeManager() {
    override fun doCollectCacheAware(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext): CompilerArgumentsCacheAware? =
        resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)?.cacheAware
}

object KotlinMPPCompilerArgumentsCacheMergeManager : CompilerArgumentsCacheMergeManager() {
    override fun doCollectCacheAware(gradleModule: IdeaModule, resolverCtx: ProjectResolverContext): CompilerArgumentsCacheAware? =
        resolverCtx.getMppModel(gradleModule)?.cacheAware
}
