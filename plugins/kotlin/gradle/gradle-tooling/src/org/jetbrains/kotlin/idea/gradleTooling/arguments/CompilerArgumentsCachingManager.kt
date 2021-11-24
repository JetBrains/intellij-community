// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper
import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument
import java.io.File

object CompilerArgumentsCachingManager {

    @Suppress("UNCHECKED_CAST")
    internal fun <TArg> cacheCompilerArgument(
        argument: TArg,
        mapper: CompilerArgumentsCacheMapper
    ): KotlinCachedCompilerArgument<*> =
        when {
            argument == null -> KotlinCachedEmptyCompilerArgument
            argument is String -> REGULAR_ARGUMENT_CACHING_STRATEGY.cacheArgument(argument, mapper)
            argument is Boolean -> BOOLEAN_ARGUMENT_CACHING_STRATEGY.cacheArgument(argument, mapper)
            argument is Array<*> -> MULTIPLE_ARGUMENT_CACHING_STRATEGY.cacheArgument(argument as Array<String>, mapper)
            else -> error("Unknown argument received" + argument.let { ": ${it::class.java.name}" })
        }

    private interface CompilerArgumentCachingStrategy<TArg, TCached> {
        fun cacheArgument(argument: TArg, mapper: CompilerArgumentsCacheMapper): TCached
    }

    private val BOOLEAN_ARGUMENT_CACHING_STRATEGY = object : CompilerArgumentCachingStrategy<Boolean, KotlinCachedBooleanCompilerArgument> {
        override fun cacheArgument(argument: Boolean, mapper: CompilerArgumentsCacheMapper): KotlinCachedBooleanCompilerArgument {
            val argStr = argument.toString()
            val id = mapper.cacheArgument(argStr)
            return KotlinCachedBooleanCompilerArgument(id)
        }
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
                val cachedArguments = argument.map { cacheCompilerArgument(it, mapper) }
                return KotlinCachedMultipleCompilerArgument(cachedArguments)
            }
        }
}
