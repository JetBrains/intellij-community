// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("KotlinUtils")
package com.intellij.util

import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.util.Pair as JBPair
import com.intellij.util.containers.toArray as toArrayFromContainers

@Deprecated(message = "moved to com.intellij.util.containers",
            replaceWith = ReplaceWith("toArray", "com.intellij.util.containers.toArray"))
fun <E> Collection<E>.toArray(empty: Array<E>): Array<E> = toArrayFromContainers(empty)

inline fun <reified T> Any?.castSafelyTo(): T? = this as? T
