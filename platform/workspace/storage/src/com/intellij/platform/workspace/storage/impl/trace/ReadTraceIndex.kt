// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.trace

import com.intellij.platform.workspace.storage.trace.ObjectToTraceMap
import com.intellij.platform.workspace.storage.trace.ReadTraceHash
import com.intellij.platform.workspace.storage.trace.ReadTraceHashSet
import com.intellij.platform.workspace.storage.trace.TraceToObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

internal class ReadTraceIndex<T> private constructor(
  objToTrace: ObjectToTraceMap<T, ReadTraceHashSet>,
  traceToObj: TraceToObjectMap<ReadTraceHash, MutableSet<T>>,
) {

  constructor() : this(ObjectToTraceMap<T, ReadTraceHashSet>(), TraceToObjectMap<ReadTraceHash, MutableSet<T>>())

  private val objToTrace: MutableMap<T, ReadTraceHashSet> = objToTrace.mapValuesTo(HashMap()) { ReadTraceHashSet(it.value) }
  private val traceToObj: Object2ObjectMap<ReadTraceHash, MutableSet<T>> = Object2ObjectOpenHashMap(traceToObj.mapValues { HashSet(it.value) })

  fun pull(another: ReadTraceIndex<T>) {
    this.objToTrace.putAll(another.objToTrace.mapValuesTo(HashMap()) { ReadTraceHashSet(it.value) })
    this.traceToObj.putAll(Object2ObjectOpenHashMap(another.traceToObj.mapValues { HashSet(it.value) }))
  }

  fun get(trace: ReadTraceHash): Set<T> {
    return traceToObj.get(trace)?.toSet() ?: emptySet()
  }

  fun get(traces: ReadTraceHashSet): Set<T> {
    return traces.flatMap { get(it) }.toSet()
  }

  fun set(traces: ReadTraceHashSet, obj: T) {
    val existingTraces = objToTrace.remove(obj)
    existingTraces?.forEach { trace ->
      val objs = traceToObj.get(trace)
      if (objs != null && trace !in traces) {
        objs.remove(obj)
        if (objs.isEmpty()) {
          traceToObj.remove(trace)
        }
      }
    }

    traces.forEach { trace ->
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
      objToTrace[obj] = traces
    }
  }
}