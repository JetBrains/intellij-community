// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CachedArgsInfo
import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument
import java.util.*

data class CachedSerializedArgsInfo(
    override val cacheOriginIdentifier: Long,
    override val currentCompilerArguments: List<KotlinCachedCompilerArgument<*>>,
    override val defaultCompilerArguments: List<KotlinCachedCompilerArgument<*>>,
    override val dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
) : CachedArgsInfo<List<KotlinCachedCompilerArgument<*>>> {
    override fun duplicate(): CachedArgsInfo<List<KotlinCachedCompilerArgument<*>>> = copy()
}

data class CachedExtractedArgsInfo(
    override val cacheOriginIdentifier: Long,
    override val currentCompilerArguments: CachedCompilerArgumentsBucket,
    override val defaultCompilerArguments: CachedCompilerArgumentsBucket,
    override val dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
) : CachedArgsInfo<CachedCompilerArgumentsBucket> {
    override fun duplicate(): CachedArgsInfo<CachedCompilerArgumentsBucket> = copy()
}
