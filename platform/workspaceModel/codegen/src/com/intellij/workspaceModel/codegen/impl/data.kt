package org.jetbrains.deft.bytes

import com.google.common.primitives.Longs
import kotlinx.io.core.Input
import kotlinx.io.core.Output

const val intBytesCount = 4

val String?.outputMaxBytes: Int
    get() =
        if (this == null) intBytesCount
        else intBytesCount + length * 4

fun Output.writeString(it: String?) {
    if (it == null) {
        writeInt(0)
        return
    }
    val bytes = it.toByteArray()
    writeInt(bytes.size)
    writeFully(bytes, 0, bytes.size)
}

fun Input.readString(): String {
    val strSize = readInt()
    val strBytes = ByteArray(strSize)
    readFully(strBytes, 0, strSize)
    return String(strBytes, Charsets.UTF_8)
}

const val useCodegenImpl: Boolean = true

val logUpdates: Boolean = System.getProperty("deft.trace.updates") != null
const val objDebugMarkers: Boolean = true
const val objDebugCheckLoaded: Boolean = true
val objStartDebugMarker: Long = Longs.fromByteArray("objStart".toByteArray())
val objEndDebugMarker: Long = Longs.fromByteArray("objEnd__".toByteArray())