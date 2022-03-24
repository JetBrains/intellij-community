// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.internal.impldep.org.apache.commons.lang.math.RandomUtils
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

interface CompilerArgumentsMapperDetachable : CompilerArgumentsCacheMapper {
    fun detachCacheAware(): CompilerArgumentsCacheAware
}

class CompilerArgumentsMapperDetachableFallback : AbstractCompilerArgumentsCacheMapper(), CompilerArgumentsMapperDetachable {
    override fun detachCacheAware(): CompilerArgumentsCacheAware =
        CompilerArgumentsCacheAwareImpl(cacheOriginIdentifier, HashMap(cacheByValueMap))

    override val cacheOriginIdentifier: Long by lazy { RandomUtils.nextLong() }
    override val offset: Int = 0
}

class CompilerArgumentsMapperDetachableImpl(
    private val masterCacheMapper: AbstractCompilerArgumentsCacheMapper,
) : AbstractCompilerArgumentsCacheMapper(), CompilerArgumentsMapperDetachable {

    override val offset: Int by lazy {
        masterCacheMapper.cacheByValueMap.keys.toIntArray().sortedArray().lastOrNull()?.plus(1) ?: 0
    }

    override fun cacheArgument(arg: String): Int {
        return if (masterCacheMapper.checkCached(arg)) {
            val key = masterCacheMapper.cacheArgument(arg)
            cacheByValueMap[key] = arg
            valueByCacheMap[arg] = key
            key
        } else super.cacheArgument(arg)
    }

    override val cacheOriginIdentifier: Long by lazy {
        masterCacheMapper.cacheOriginIdentifier
    }

    override fun detachCacheAware(): CompilerArgumentsCacheAware {
        val uniqueKeys = cacheByValueMap.keys - masterCacheMapper.distributeCacheIds().toSet()
        uniqueKeys.forEach {
            val key = it
            val value = cacheByValueMap.getValue(it)
            masterCacheMapper.cacheByValueMap[key] = value
            masterCacheMapper.valueByCacheMap[value] = key
        }

        return CompilerArgumentsCacheAwareImpl(
            cacheOriginIdentifier,
            HashMap(uniqueKeys.associateWith { cacheByValueMap.getValue(it) })
        )
    }
}
