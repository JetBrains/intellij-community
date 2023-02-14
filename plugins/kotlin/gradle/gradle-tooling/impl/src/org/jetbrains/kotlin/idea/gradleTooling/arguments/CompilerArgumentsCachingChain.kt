// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.api.Task
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

object CompilerArgumentsCachingChain {
    fun extractAndCacheTask(
        compileTask: Task,
        mapper: CompilerArgumentsCacheMapper,
        defaultsOnly: Boolean = false
    ): CachedCompilerArgumentsBucket =
        CompilerArgumentsExtractor.extractCompilerArgumentsFromTask(compileTask, defaultsOnly).let {
            CompilerArgumentsCachingManager.cacheCompilerArguments(it, mapper)
        }
}
