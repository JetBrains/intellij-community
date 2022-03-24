// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCachingManager.cacheCompilerArgument
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

object CompilerArgumentsCachingTool {
    fun cacheCompilerArguments(
        extractedBucket: ExtractedCompilerArgumentsBucket,
        mapper: CompilerArgumentsCacheMapper
    ): CachedCompilerArgumentsBucket {
        val cachedCompilerArgumentsClassName =
            KotlinCachedRegularCompilerArgument(mapper.cacheArgument(extractedBucket.compilerArgumentsClassName))
        val cachedSingleArguments = extractedBucket.singleArguments.entries.associate { it.cacheEntry(mapper) }
        val cachedClasspathParts =
            extractedBucket.classpathParts.map { cacheCompilerArgument(it, mapper) }
                .let { KotlinCachedMultipleCompilerArgument(it) }
        val cachedMultipleArguments = extractedBucket.multipleArguments.entries.associate { it.cacheEntry(mapper) }
        val cachedFlagArguments = extractedBucket.flagArguments.entries.associate { it.cacheEntry(mapper) }
        val cachedInternalArguments = extractedBucket.internalArguments.map { cacheCompilerArgument(it, mapper) }
        val cachedFreeArgs = extractedBucket.freeArgs.map { cacheCompilerArgument(it, mapper) }
        return CachedCompilerArgumentsBucket(
            cachedCompilerArgumentsClassName,
            cachedSingleArguments,
            cachedClasspathParts,
            cachedMultipleArguments,
            cachedFlagArguments,
            cachedInternalArguments,
            cachedFreeArgs
        )
    }

    private fun <TKey, TVal> Map.Entry<TKey, TVal>.cacheEntry(mapper: CompilerArgumentsCacheMapper) =
        cacheCompilerArgument(key, mapper) to cacheCompilerArgument(value, mapper)

}
