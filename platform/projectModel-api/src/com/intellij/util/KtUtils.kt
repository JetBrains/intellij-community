// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")

package com.intellij.util

import com.intellij.openapi.util.Pair as JBPair
import com.intellij.util.containers.toArray as toArrayFromContainers

operator fun <A> JBPair<A, *>.component1(): A = first
operator fun <A> JBPair<*, A>.component2(): A = second

// This function helps to get rid of platform types
fun <A : Any, B : Any> JBPair<A?, B?>.toNotNull(): Pair<A, B> {
  return requireNotNull(first) to requireNotNull(second)
}

@Deprecated(message = "moved to com.intellij.util.containers",
            replaceWith = ReplaceWith("toArray", "com.intellij.util.containers.toArray"))
fun <E> Collection<E>.toArray(empty: Array<E>): Array<E> = toArrayFromContainers(empty)

inline fun <reified T> Any?.castSafelyTo(): T? = this as? T