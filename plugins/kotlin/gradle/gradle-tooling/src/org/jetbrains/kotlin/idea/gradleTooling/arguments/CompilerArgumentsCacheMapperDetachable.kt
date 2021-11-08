// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

interface CompilerArgumentsMapperDetachable : CompilerArgumentsCacheMapper {
    fun detachCacheAware(): CompilerArgumentsCacheAware
}

class CompilerArgumentsMapperDetachableImpl(private val masterCacheMapper: AbstractCompilerArgumentsCacheMapper) :
    AbstractCompilerArgumentsCacheMapper(),
    CompilerArgumentsMapperDetachable {

    override val offset: Int = masterCacheMapper.cacheByValueMap.keys.toIntArray().sortedArray().lastOrNull()?.plus(1) ?: 0

    override val cacheOriginIdentifier: Long
        get() = masterCacheMapper.cacheOriginIdentifier

    override fun cacheArgument(arg: String): Int {
        return if (masterCacheMapper.checkCached(arg)) masterCacheMapper.cacheArgument(arg)
        else super.cacheArgument(arg)
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
