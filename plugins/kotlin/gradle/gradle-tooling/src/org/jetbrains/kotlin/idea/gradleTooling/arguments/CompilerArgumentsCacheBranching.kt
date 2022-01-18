// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.internal.impldep.org.apache.commons.lang.math.RandomUtils
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheMapper
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

interface CompilerArgumentsCacheBranching : CompilerArgumentsCacheMapper {
    fun branchOffDetachable(): CompilerArgumentsMapperDetachable
}

class CompilerArgumentsCacheBranchingImpl(override val cacheOriginIdentifier: Long = RandomUtils.nextLong()) :
    AbstractCompilerArgumentsCacheMapper(), CompilerArgumentsCacheBranching {
    override fun branchOffDetachable(): CompilerArgumentsMapperDetachable = CompilerArgumentsMapperDetachableImpl(this)
    override val offset: Int = 0
}

val CACHE_MAPPER_BRANCHING =
    ModelBuilderContext.DataProvider<CompilerArgumentsCacheBranching> { _, _ -> CompilerArgumentsCacheBranchingImpl() }
