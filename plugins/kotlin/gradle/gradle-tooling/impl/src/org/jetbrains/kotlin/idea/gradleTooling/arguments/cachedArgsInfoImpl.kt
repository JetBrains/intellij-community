// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CachedArgsInfo
import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument

fun createCachedArgsInfo(cachedArgsInfo: CachedArgsInfo<*>, cloningCache: MutableMap<Any, Any>): CachedArgsInfo<*> =
    when (cachedArgsInfo) {
        is CachedSerializedArgsInfo -> cloningCache.getOrPut(cachedArgsInfo) {
            CachedSerializedArgsInfo(
                cachedArgsInfo.cacheOriginIdentifier,
                cachedArgsInfo.currentCompilerArguments.map { KotlinCachedRegularCompilerArgument(it, cloningCache) },
                cachedArgsInfo.defaultCompilerArguments.map { KotlinCachedRegularCompilerArgument(it, cloningCache) },
                cachedArgsInfo.dependencyClasspath.mapNotNull { KotlinCachedCompilerArgument(it, cloningCache) }
            )
        } as CachedSerializedArgsInfo
        is CachedExtractedArgsInfo -> cloningCache.getOrPut(cachedArgsInfo) {
            CachedExtractedArgsInfo(
                cachedArgsInfo.cacheOriginIdentifier,
                CachedCompilerArgumentsBucket(cachedArgsInfo.currentCompilerArguments, cloningCache),
                CachedCompilerArgumentsBucket(cachedArgsInfo.defaultCompilerArguments, cloningCache),
                cachedArgsInfo.dependencyClasspath.mapNotNull { KotlinCachedCompilerArgument(it, cloningCache) }
            )
        } as CachedExtractedArgsInfo
        else -> {
            error("")
        }
    }

interface CachedSerializedArgsInfo : CachedArgsInfo<List<KotlinCachedRegularCompilerArgument>> {
    override val cacheOriginIdentifier: Long
    override val currentCompilerArguments: List<KotlinCachedRegularCompilerArgument>
    override val defaultCompilerArguments: List<KotlinCachedRegularCompilerArgument>
    override val dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
}

fun CachedSerializedArgsInfo(
    cacheOriginIdentifier: Long,
    currentCompilerArguments: List<KotlinCachedRegularCompilerArgument>,
    defaultCompilerArguments: List<KotlinCachedRegularCompilerArgument>,
    dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
): CachedSerializedArgsInfo =
    CachedSerializedArgsInfoImpl(cacheOriginIdentifier, currentCompilerArguments, defaultCompilerArguments, dependencyClasspath)

fun CachedSerializedArgsInfo(cachedArgsInfo: CachedSerializedArgsInfo): CachedSerializedArgsInfo =
    CachedSerializedArgsInfo(
        cachedArgsInfo.cacheOriginIdentifier,
        cachedArgsInfo.currentCompilerArguments,
        cachedArgsInfo.defaultCompilerArguments,
        cachedArgsInfo.dependencyClasspath
    )

private data class CachedSerializedArgsInfoImpl(
    override val cacheOriginIdentifier: Long,
    override val currentCompilerArguments: List<KotlinCachedRegularCompilerArgument>,
    override val defaultCompilerArguments: List<KotlinCachedRegularCompilerArgument>,
    override val dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
) : CachedSerializedArgsInfo


interface CachedExtractedArgsInfo : CachedArgsInfo<CachedCompilerArgumentsBucket> {
    override val cacheOriginIdentifier: Long
    override val currentCompilerArguments: CachedCompilerArgumentsBucket
    override val defaultCompilerArguments: CachedCompilerArgumentsBucket
    override val dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
}

fun CachedExtractedArgsInfo(
    cacheOriginIdentifier: Long,
    currentCompilerArguments: CachedCompilerArgumentsBucket,
    defaultCompilerArguments: CachedCompilerArgumentsBucket,
    dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
): CachedExtractedArgsInfo =
    CachedExtractedArgsInfoImpl(cacheOriginIdentifier, currentCompilerArguments, defaultCompilerArguments, dependencyClasspath)

fun CachedExtractedArgsInfo(cachedArgsInfo: CachedExtractedArgsInfo, cloningCache: MutableMap<Any, Any>): CachedExtractedArgsInfo =
    CachedExtractedArgsInfo(
        cachedArgsInfo.cacheOriginIdentifier,
        CachedCompilerArgumentsBucket(cachedArgsInfo.currentCompilerArguments, cloningCache),
        CachedCompilerArgumentsBucket(cachedArgsInfo.defaultCompilerArguments, cloningCache),
        cachedArgsInfo.dependencyClasspath
    )

private data class CachedExtractedArgsInfoImpl(
    override val cacheOriginIdentifier: Long,
    override val currentCompilerArguments: CachedCompilerArgumentsBucket,
    override val defaultCompilerArguments: CachedCompilerArgumentsBucket,
    override val dependencyClasspath: Collection<KotlinCachedCompilerArgument<*>>
) : CachedExtractedArgsInfo