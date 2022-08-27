// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.gradleTooling.arguments

import org.jetbrains.kotlin.idea.projectModel.KotlinCachedCompilerArgument

fun KotlinCachedCompilerArgument(
    cachedCompilerArgument: KotlinCachedCompilerArgument<*>,
    cloningCache: MutableMap<Any, Any>
): KotlinCachedCompilerArgument<*>? = when (cachedCompilerArgument) {
    is KotlinCachedRegularCompilerArgument -> KotlinCachedRegularCompilerArgument(cachedCompilerArgument, cloningCache)
    is KotlinCachedMultipleCompilerArgument -> KotlinCachedMultipleCompilerArgument(cachedCompilerArgument, cloningCache)
    is KotlinCachedBooleanCompilerArgument -> cloningCache.getOrPut(cachedCompilerArgument) {
        KotlinCachedBooleanCompilerArgument(cachedCompilerArgument.data)
    } as KotlinCachedBooleanCompilerArgument
    else -> {
        //TODO add logging
        null
    }
}

interface KotlinCachedRegularCompilerArgument : KotlinCachedCompilerArgument<Int> {
    override val data: Int
}

fun KotlinCachedRegularCompilerArgument(data: Int): KotlinCachedRegularCompilerArgument = KotlinCachedRegularCompilerArgumentImpl(data)
fun KotlinCachedRegularCompilerArgument(
    argument: KotlinCachedRegularCompilerArgument,
    cloningCache: MutableMap<Any, Any>
): KotlinCachedRegularCompilerArgument =
    cloningCache.getOrPut(argument) {
        KotlinCachedRegularCompilerArgument(argument.data)
    } as KotlinCachedRegularCompilerArgument

private data class KotlinCachedRegularCompilerArgumentImpl(override val data: Int) : KotlinCachedRegularCompilerArgument

interface KotlinCachedMultipleCompilerArgument : KotlinCachedCompilerArgument<List<KotlinCachedRegularCompilerArgument>> {
    override val data: List<KotlinCachedRegularCompilerArgument>
}

fun KotlinCachedMultipleCompilerArgument(data: List<KotlinCachedRegularCompilerArgument>): KotlinCachedMultipleCompilerArgument =
    KotlinCachedMultipleCompilerArgumentImpl(data)

fun KotlinCachedMultipleCompilerArgument(
    argument: KotlinCachedMultipleCompilerArgument,
    cloningCache: MutableMap<Any, Any>
): KotlinCachedMultipleCompilerArgument =
    cloningCache.getOrPut(argument) {
        KotlinCachedMultipleCompilerArgument(argument.data.map { el ->
            cloningCache.getOrPut(el) { KotlinCachedRegularCompilerArgument(el.data) } as KotlinCachedRegularCompilerArgument
        })
    } as KotlinCachedMultipleCompilerArgument

private data class KotlinCachedMultipleCompilerArgumentImpl(override val data: List<KotlinCachedRegularCompilerArgument>) :
    KotlinCachedMultipleCompilerArgument

interface KotlinCachedBooleanCompilerArgument : KotlinCachedCompilerArgument<Boolean> {
    override val data: Boolean
}

fun KotlinCachedBooleanCompilerArgument(data: Boolean): KotlinCachedBooleanCompilerArgument = KotlinCachedBooleanCompilerArgumentImpl(data)
fun KotlinCachedBooleanCompilerArgument(
    argument: KotlinCachedBooleanCompilerArgument,
    cloningCache: MutableMap<Any, Any>
): KotlinCachedBooleanCompilerArgument =
    cloningCache.getOrPut(argument) {
        KotlinCachedBooleanCompilerArgument(argument.data)
    } as KotlinCachedBooleanCompilerArgument

private data class KotlinCachedBooleanCompilerArgumentImpl(override val data: Boolean) : KotlinCachedBooleanCompilerArgument
