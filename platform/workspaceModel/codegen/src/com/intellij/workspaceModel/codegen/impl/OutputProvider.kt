package org.jetbrains.deft.bytes

import kotlinx.io.core.Output
import org.jetbrains.deft.ObjId

interface OutputProvider : AutoCloseable {
    suspend fun beginObject(id: ObjId<*>, remainingObjectsEstimateSize: Int): Output
    fun endObject(): LocalUncommittedObjectData
}

class SharedMemRange(val position: Long, val size: Int) {
  override fun toString(): String = "$position..+$size"
}

class LocalUncommittedObjectData(
  val id: ObjId<*>,
  val isLive: Boolean,
  val segment: SharedMemRange,
) {
  override fun toString(): String = "$id: $segment"
}