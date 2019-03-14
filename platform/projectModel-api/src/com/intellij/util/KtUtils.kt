// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")

package com.intellij.util

import java.util.*
import com.intellij.openapi.util.Pair as JBPair

operator fun <A> JBPair<A, *>.component1(): A = first
operator fun <A> JBPair<*, A>.component2(): A = second

// This function helps to get rid of platform types
fun <A : Any, B : Any> JBPair<A?, B?>.toNotNull(): Pair<A, B> {
  return requireNotNull(first) to requireNotNull(second)
}

inline fun <reified E : Enum<E>, V> enumMapOf(): MutableMap<E, V> = EnumMap<E, V>(E::class.java)

fun <E> Collection<E>.toArray(empty: Array<E>): Array<E> {
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
  return (this as java.util.Collection<E>).toArray(empty)
}

fun <T> lazyPub(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.PUBLICATION, initializer)

inline fun <reified T> Any?.castSafelyTo(): T? = this as? T