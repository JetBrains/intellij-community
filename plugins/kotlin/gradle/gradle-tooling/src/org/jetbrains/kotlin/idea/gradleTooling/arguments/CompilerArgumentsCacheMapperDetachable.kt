// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

interface CompilerArgumentsMapperDetachable : CompilerArgumentsCacheMapper {
    fun detachCacheAware(): CompilerArgumentsCacheAware
}

class CompilerArgumentsMapperDetachableImpl(private val masterCacheMapper: CompilerArgumentsCacheMapper) :
    AbstractCompilerArgumentsCacheMapper(),
    CompilerArgumentsMapperDetachable {

    override val cacheOriginIdentifier: Long
        get() = masterCacheMapper.cacheOriginIdentifier

    override fun cacheArgument(arg: String): Int =
        if (masterCacheMapper.checkCached(arg))
            masterCacheMapper.cacheArgument(arg)
        else masterCacheMapper.cacheArgument(arg).also {
            cacheByValueMap[it] = arg
            valueByCacheMap[arg] = it
        }

    override fun detachCacheAware(): CompilerArgumentsCacheAware = CompilerArgumentsCacheAwareImpl(this)
}
