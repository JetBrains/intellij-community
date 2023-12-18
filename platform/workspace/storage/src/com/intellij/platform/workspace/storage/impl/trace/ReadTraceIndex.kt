// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.trace

import com.intellij.platform.workspace.storage.trace.ObjectToTraceMap
import com.intellij.platform.workspace.storage.trace.ReadTraceHash
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

internal class ReadTraceIndex<T> private constructor(
  objToTrace: ObjectToTraceMap<T, IntOpenHashSet>,
  traceToObj: Int2ObjectOpenHashMap<MutableSet<T>>,
) {

  constructor() : this(ObjectToTraceMap<T, IntOpenHashSet>(), Int2ObjectOpenHashMap<MutableSet<T>>())

  private val objToTrace: MutableMap<T, IntOpenHashSet> = objToTrace.mapValuesTo(HashMap()) { IntOpenHashSet(it.value) }
  private val traceToObj: Int2ObjectOpenHashMap<MutableSet<T>> = Int2ObjectOpenHashMap(traceToObj.mapValues { HashSet(it.value) })

  fun pull(another: ReadTraceIndex<T>) {
    this.objToTrace.putAll(another.objToTrace.mapValuesTo(HashMap()) { IntOpenHashSet(it.value) })
    this.traceToObj.putAll(Int2ObjectOpenHashMap(another.traceToObj.mapValues { HashSet(it.value) }))
  }

  fun get(trace: ReadTraceHash): Set<T> {
    return traceToObj.get(trace.hash)?.toSet() ?: emptySet()
  }

  fun get(traces: ReadTraceHashSet): Set<T> {
    return traces.flatMap { get(it) }.toSet()
  }

  fun set(traces: ReadTraceHashSet, obj: T) {
    val existingTraces = objToTrace.remove(obj)
    val hashSetTraces = IntOpenHashSet(traces.size).also { set -> traces.forEach { set.add(it.hash) } }
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