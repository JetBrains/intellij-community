// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsCacheAware
import java.io.Serializable

class CompilerArgumentsCacheHolder : Serializable {
    private val cacheAwareWithMergeByIdentifier: HashMap<Long, CompilerArgumentsCacheAwareWithMerge> = hashMapOf()

    fun getCacheAware(originIdentifier: Long): CompilerArgumentsCacheAware? = cacheAwareWithMergeByIdentifier[originIdentifier]

    fun mergeCacheAware(cacheAware: CompilerArgumentsCacheAware) {
        val cacheAwareWithMerge = cacheAwareWithMergeByIdentifier.getOrPut(cacheAware.cacheOriginIdentifier) {
            CompilerArgumentsCacheAwareWithMerge(cacheAware.cacheOriginIdentifier)
        }
        cacheAwareWithMerge.mergeCacheAware(cacheAware)
    }

    companion object {
        private class CompilerArgumentsCacheAwareWithMerge(override val cacheOriginIdentifier: Long) :
            AbstractCompilerArgumentsCacheAware() {
            override val cacheByValueMap: HashMap<Int, String> = hashMapOf()

            fun mergeCacheAware(cacheAware: CompilerArgumentsCacheAware) {
                val cacheAwareMap = cacheAware.distributeCacheIds().associateWith { cacheAware.getCached(it)!! }
                check(cacheByValueMap.keys.intersect(cacheAwareMap.keys).all { cacheByValueMap[it] == cacheAwareMap[it] }) {
                    "Compiler arguments caching failure! Trying to merge cache with existing ID but different value!"
                }
                val cacheAwareMapReversed = cacheAwareMap.entries.associate { it.value to it.key }
                check(cacheAwareMap.size == cacheAwareMapReversed.size) {
                    "Compiler arguments caching failure! Cache with non unique cached values detected!"
                }
                cacheByValueMap.putAll(cacheAwareMap)
            }
        }
    }
}
