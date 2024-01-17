// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.trace

import com.intellij.platform.workspace.storage.trace.ObjectToTraceMap
import com.intellij.platform.workspace.storage.trace.ReadTraceHash
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet

internal class ReadTraceIndex<T> private constructor(
  objToTrace: ObjectToTraceMap<T, LongOpenHashSet>,
  traceToObj: Long2ObjectOpenHashMap<MutableSet<T>>,
) {

  constructor() : this(ObjectToTraceMap<T, LongOpenHashSet>(), Long2ObjectOpenHashMap<MutableSet<T>>())

  private val objToTrace: MutableMap<T, LongOpenHashSet> = objToTrace.mapValuesTo(HashMap()) { LongOpenHashSet(it.value) }
  private val traceToObj: Long2ObjectOpenHashMap<MutableSet<T>> = Long2ObjectOpenHashMap(traceToObj.mapValues { HashSet(it.value) })

  fun pull(another: ReadTraceIndex<T>) {
    this.objToTrace.putAll(another.objToTrace.mapValuesTo(HashMap()) { LongOpenHashSet(it.value) })
    this.traceToObj.putAll(Long2ObjectOpenHashMap(another.traceToObj.mapValues { HashSet(it.value) }))
  }

  fun get(trace: ReadTraceHash): Set<T> {
    return traceToObj.get(trace.hash)?.toSet() ?: emptySet()
  }

  fun get(traces: ReadTraceHashSet): Set<T> {
    return traces.flatMap { get(it) }.toSet()
  }

  fun set(traces: ReadTraceHashSet, obj: T) {
    val existingTraces = objToTrace.remove(obj)
    val hashSetTraces = LongOpenHashSet(traces.size).also { set -> traces.forEach { set.add(it.hash) } }
    existingTraces?.forEach { trace ->
      val objs = traceToObj.get(trace)
      if (objs != null && trace !in hashSetTraces) {
        objs.remove(obj)
        if (objs.isEmpty()) {
          traceToObj.remove(trace)
        }
      }
    }

    hashSetTraces.forEach { trace ->
      if (existingTraces == null || trace !in existingTraces) {
        val objs = traceToObj.get(trace)
        if (objs == null) {
          traceToObj[trace] = mutableSetOf(obj)
        }
        else {
          objs.add(obj)
        }
      }
    }

    if (traces.isNotEmpty()) {
      objToTrace[obj] = hashSetTraces
    }
  }
}