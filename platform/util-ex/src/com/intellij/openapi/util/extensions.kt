// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util

/** Allows deconstruction of `com.intellij.openapi.util.Pair`. */
operator fun <A, B> Pair<A, B>.component1(): A = this.first
operator fun <A, B> Pair<A, B>.component2(): B = this.second

/** Helps to get rid of platform types. */
fun <A : Any, B : Any> Pair<A?, B?>.toNotNull(): kotlin.Pair<A, B> = requireNotNull(first) to requireNotNull(second)