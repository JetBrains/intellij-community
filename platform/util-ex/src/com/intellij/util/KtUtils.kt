// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")
package com.intellij.util

import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.util.Pair as JBPair
import com.intellij.util.containers.toArray as toArrayFromContainers

@Deprecated("use `com.intellij.openapi.util.component1`", replaceWith = ReplaceWith("component1", "com.intellij.openapi.util.component1"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
operator fun <A> JBPair<A, *>.component1(): A = first

@Deprecated("use `com.intellij.openapi.util.component2`", replaceWith = ReplaceWith("component2", "com.intellij.openapi.util.component2"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
operator fun <A> JBPair<*, A>.component2(): A = second

@Deprecated("use `com.intellij.openapi.util.toNotNull`", replaceWith = ReplaceWith("toNotNull", "com.intellij.openapi.util.toNotNull"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
fun <A : Any, B : Any> JBPair<A?, B?>.toNotNull(): Pair<A, B> {
  return requireNotNull(first) to requireNotNull(second)
}

@Deprecated(message = "moved to com.intellij.util.containers",
            replaceWith = ReplaceWith("toArray", "com.intellij.util.containers.toArray"))
fun <E> Collection<E>.toArray(empty: Array<E>): Array<E> = toArrayFromContainers(empty)

inline fun <reified T> Any?.castSafelyTo(): T? = this as? T
