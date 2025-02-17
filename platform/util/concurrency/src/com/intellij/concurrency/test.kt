// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.util.containers.ConcurrentIntObjectMap
import com.intellij.util.containers.ConcurrentLongObjectMap
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentMap

@TestOnly
internal fun <K : Any, V: Any> chMap(): ConcurrentMap<K, V> = ConcurrentHashMap()

@TestOnly
internal fun <K : Any, V: Any> chMap(from: Map<K, V>): ConcurrentMap<K, V> = ConcurrentHashMap(from)

@TestOnly
internal fun <V: Any> chLongMap(): ConcurrentLongObjectMap<V> = ConcurrentLongObjectHashMap<V>()

internal fun <V: Any> chIntMap(): ConcurrentIntObjectMap<V> = ConcurrentIntObjectHashMap<V>()

//internal fun <K : Any, V: Any> chMap(from: Map<K, V>): ConcurrentMap<K, V> = ConcurrentHashMap(from)