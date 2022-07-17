// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.internal.impldep.org.apache.commons.lang.math.RandomUtils
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper

interface CompilerArgumentsMapperDetachable : CompilerArgumentsCacheMapper {
    fun detachCacheAware(): CompilerArgumentsCacheAware
}

class CompilerArgumentsMapperDetachableImpl : AbstractCompilerArgumentsCacheMapper(), CompilerArgumentsMapperDetachable {
    override fun detachCacheAware(): CompilerArgumentsCacheAware =
        CompilerArgumentsCacheAwareImpl(cacheOriginIdentifier, HashMap(cacheByValueMap))

    override val cacheOriginIdentifier: Long by lazy { RandomUtils.nextLong() }
}