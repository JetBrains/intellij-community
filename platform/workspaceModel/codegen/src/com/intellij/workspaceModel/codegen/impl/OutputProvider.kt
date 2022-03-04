package org.jetbrains.deft.bytes

import kotlinx.io.core.Output
import org.jetbrains.deft.ObjId
import org.jetbrains.deft.rpc.LocalUncommittedObjectData

interface OutputProvider : AutoCloseable {
    suspend fun beginObject(id: ObjId<*>, remainingObjectsEstimateSize: Int): Output
    fun endObject(): LocalUncommittedObjectData
}