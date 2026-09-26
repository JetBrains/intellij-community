package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG

@ApiStatus.Internal
object X11NativeMemory {
  @JvmStatic
  fun clientMessage(arena: Arena, display: Long, window: Long, type: Long, data: LongArray): MemorySegment {
    require(ADDRESS.byteSize() == Long.SIZE_BYTES.toLong())
    require(data.size <= 5)
    val event = arena.allocate(24L * Long.SIZE_BYTES, Long.SIZE_BYTES.toLong())
    event.set(JAVA_INT, 0, 33)
    event.set(JAVA_INT, 16, 1)
    event.set(ADDRESS, 24, MemorySegment.ofAddress(display))
    event.set(JAVA_LONG, 32, window)
    event.set(JAVA_LONG, 40, type)
    event.set(JAVA_INT, 48, 32)
    for (index in data.indices) event.set(JAVA_LONG, 56 + index.toLong() * Long.SIZE_BYTES, data[index])
    return event
  }
}
