// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper
import java.util.*

abstract class AbstractCompilerArgumentsCacheMapper protected constructor() :
    AbstractCompilerArgumentsCacheAware(), CompilerArgumentsCacheMapper {
    private var nextId = 0
    final override var cacheByValueMap: HashMap<Int, String> = hashMapOf()
    private var valueByCacheMap: HashMap<String, Int> = hashMapOf()

    override fun cacheArgument(arg: String): Int {
        if (checkCached(arg)) return valueByCacheMap.getValue(arg)
        val retVal = nextId
        nextId += 1
        return retVal.also {
            cacheByValueMap[it] = arg
            valueByCacheMap[arg] = it
        }
    }

    override fun checkCached(arg: String): Boolean = valueByCacheMap.containsKey(arg)
}

data class CompilerArgumentsCacheMapperImpl(override val cacheOriginIdentifier: Long = Random().nextLong()) :
    AbstractCompilerArgumentsCacheMapper() {
}
