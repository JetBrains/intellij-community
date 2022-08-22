// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

object CompilerArgumentsCachingManager {
    fun cacheCompilerArguments(
        extractedBucket: ExtractedCompilerArgumentsBucket,
        mapper: CompilerArgumentsCacheMapper
    ): CachedCompilerArgumentsBucket {
        val cachedCompilerArgumentsClassName =
            REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(extractedBucket.compilerArgumentsClassName, mapper)
        val cachedSingleArguments = extractedBucket.singleArguments.entries.associate { (k, v) ->
            REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(k, mapper) to
                    v?.let { REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(it, mapper) }
        }
        val cachedClasspathParts = extractedBucket.classpathParts.map { REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(it, mapper) }
            .let { KotlinCachedMultipleCompilerArgument(it) }
        val cachedMultipleArguments = extractedBucket.multipleArguments.entries.associate { (k, v) ->
            REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(k, mapper) to
                    MULTIPLE_ARGUMENT_CACHING_STRATEGY.cacheArgument(v ?: emptyArray(), mapper)

        }
        val cachedFlagArguments = extractedBucket.flagArguments.entries.associate { (k, v) ->
            REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(k, mapper) to KotlinCachedBooleanCompilerArgument(v)
        }
        val cachedInternalArguments = extractedBucket.internalArguments.map { REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(it, mapper) }
        val cachedFreeArgs = extractedBucket.freeArgs.map { REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(it, mapper) }
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

    private interface CompilerArgumentCachingStrategy<TArg, TCached> {
        fun cacheArgument(argument: TArg, mapper: CompilerArgumentsCacheMapper): TCached
    }

    private val REGULAR_ARGUMENT_CACHING_STRATEGY = object : CompilerArgumentCachingStrategy<String, KotlinCachedRegularCompilerArgument> {
        override fun cacheArgument(argument: String, mapper: CompilerArgumentsCacheMapper): KotlinCachedRegularCompilerArgument {
            val id = mapper.cacheArgument(argument)
            return KotlinCachedRegularCompilerArgument(id)
        }
    }

    private val MULTIPLE_ARGUMENT_CACHING_STRATEGY =
        object : CompilerArgumentCachingStrategy<Array<String>, KotlinCachedMultipleCompilerArgument> {
            override fun cacheArgument(
                argument: Array<String>,
                mapper: CompilerArgumentsCacheMapper
            ): KotlinCachedMultipleCompilerArgument {
                val cachedArguments = argument.map { REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(it, mapper) }
                return KotlinCachedMultipleCompilerArgument(cachedArguments)
            }
        }
}
