// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.gradle.internal.impldep.org.apache.commons.lang.math.RandomUtils
import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import kotlin.collections.HashMap

/**
 * Abstract compiler arguments cache aware
 *
 * @constructor Create empty Abstract compiler arguments cache aware
 */
abstract class AbstractCompilerArgumentsCacheAware : CompilerArgumentsCacheAware {
    abstract val cacheByValueMap: HashMap<Int, String>

    override fun getCached(cacheId: Int): String? = cacheByValueMap[cacheId]
    override fun distributeCacheIds(): Iterable<Int> = cacheByValueMap.keys
}

data class CompilerArgumentsCacheAwareImpl(
    override val cacheOriginIdentifier: Long = RandomUtils.nextLong(),
    override val cacheByValueMap: HashMap<Int, String> = hashMapOf(),
) : AbstractCompilerArgumentsCacheAware() {
    constructor(cacheAware: CompilerArgumentsCacheAware) : this(
        cacheOriginIdentifier = cacheAware.cacheOriginIdentifier,
        cacheByValueMap = HashMap(cacheAware.distributeCacheIds().mapNotNull { id -> cacheAware.getCached(id)?.let { id to it } }.toMap())
    )
}
