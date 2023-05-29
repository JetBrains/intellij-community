// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.vfslog

import java.util.concurrent.atomic.AtomicInteger

class IntHistogram(splitPoints: List<Int>) {
  val intervals =
    listOf(Int.MIN_VALUE until splitPoints[0]) +
    splitPoints.windowed(2) { it[0] until it[1] } +
    listOf(splitPoints.last()..Int.MAX_VALUE)

  val counts = MutableList(intervals.size) { AtomicInteger(0) }

  fun add(point: Int) {
    val index = intervals.indexOfFirst { it.contains(point) }
    counts[index].incrementAndGet()
  }

  override fun toString(): String {
    return intervals.zip(counts).filter { it.second.get() > 0 }
      .joinToString(", ", "[", "]") { "${it.first}=${it.second}" }
  }
}