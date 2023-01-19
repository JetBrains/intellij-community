// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.CompilerArgumentsClassNameAware
import java.io.Serializable

interface ExtractedCompilerArgumentsBucket : CompilerArgumentsClassNameAware<String>, Serializable {
    override val compilerArgumentsClassName: String
    val singleArguments: Map<String, String?>
    val classpathParts: Array<String>
    val multipleArguments: Map<String, Array<String>?>
    val flagArguments: Map<String, Boolean>
    val internalArguments: List<String>
    val freeArgs: List<String>
}

fun ExtractedCompilerArgumentsBucket(
    compilerArgumentsClassName: String,
    singleArguments: Map<String, String?> = emptyMap(),
    classpathParts: Array<String> = emptyArray(),
    multipleArguments: Map<String, Array<String>?> = emptyMap(),
    flagArguments: Map<String, Boolean> = emptyMap(),
    internalArguments: List<String> = emptyList(),
    freeArgs: List<String> = emptyList()
): ExtractedCompilerArgumentsBucket = ExtractedCompilerArgumentsBucketImpl(
    compilerArgumentsClassName, singleArguments, classpathParts, multipleArguments, flagArguments, internalArguments, freeArgs
)

private class ExtractedCompilerArgumentsBucketImpl(
    override val compilerArgumentsClassName: String,
    override val singleArguments: Map<String, String?>,
    override val classpathParts: Array<String>,
    override val multipleArguments: Map<String, Array<String>?>,
    override val flagArguments: Map<String, Boolean>,
    override val internalArguments: List<String>,
    override val freeArgs: List<String>
) : ExtractedCompilerArgumentsBucket

interface CachedCompilerArgumentsBucket : CompilerArgumentsClassNameAware<KotlinCachedRegularCompilerArgument>, Serializable {
    override val compilerArgumentsClassName: KotlinCachedRegularCompilerArgument
    val singleArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedRegularCompilerArgument?>
    val classpathParts: KotlinCachedMultipleCompilerArgument
    val multipleArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedMultipleCompilerArgument?>
    val flagArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedBooleanCompilerArgument>
    val internalArguments: List<KotlinCachedRegularCompilerArgument>
    val freeArgs: List<KotlinCachedRegularCompilerArgument>
}

fun CachedCompilerArgumentsBucket(
    compilerArgumentsClassName: KotlinCachedRegularCompilerArgument,
    singleArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedRegularCompilerArgument?>,
    classpathParts: KotlinCachedMultipleCompilerArgument,
    multipleArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedMultipleCompilerArgument>,
    flagArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedBooleanCompilerArgument>,
    internalArguments: List<KotlinCachedRegularCompilerArgument>,
    freeArgs: List<KotlinCachedRegularCompilerArgument>,
): CachedCompilerArgumentsBucket = CachedCompilerArgumentsBucketImpl(
    compilerArgumentsClassName, singleArguments, classpathParts, multipleArguments, flagArguments, internalArguments, freeArgs
)

fun CachedCompilerArgumentsBucket(bucket: CachedCompilerArgumentsBucket,
                                  cloningCache: MutableMap<Any, Any>): CachedCompilerArgumentsBucket = CachedCompilerArgumentsBucketImpl(
    KotlinCachedRegularCompilerArgument(bucket.compilerArgumentsClassName, cloningCache),
    bucket.singleArguments.map { (k, v) ->
        KotlinCachedRegularCompilerArgument(k, cloningCache) to v?.let { KotlinCachedRegularCompilerArgument(it, cloningCache) }
    }.toMap(),
    KotlinCachedMultipleCompilerArgument(bucket.classpathParts, cloningCache),
    bucket.multipleArguments.map { (k, v) ->
        KotlinCachedRegularCompilerArgument(k, cloningCache) to v?.let { KotlinCachedMultipleCompilerArgument(it, cloningCache) }
    }.toMap(),
    bucket.flagArguments.map { (k, v) ->
        KotlinCachedRegularCompilerArgument(k, cloningCache) to KotlinCachedBooleanCompilerArgument(v.data)
    }.toMap(),
    bucket.internalArguments.map { KotlinCachedRegularCompilerArgument(it, cloningCache) },
    bucket.freeArgs.map { KotlinCachedRegularCompilerArgument(it, cloningCache) }
)

private class CachedCompilerArgumentsBucketImpl(
    override val compilerArgumentsClassName: KotlinCachedRegularCompilerArgument,
    override val singleArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedRegularCompilerArgument?>,
    override val classpathParts: KotlinCachedMultipleCompilerArgument,
    override val multipleArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedMultipleCompilerArgument?>,
    override val flagArguments: Map<KotlinCachedRegularCompilerArgument, KotlinCachedBooleanCompilerArgument>,
    override val internalArguments: List<KotlinCachedRegularCompilerArgument>,
    override val freeArgs: List<KotlinCachedRegularCompilerArgument>
) : CachedCompilerArgumentsBucket
