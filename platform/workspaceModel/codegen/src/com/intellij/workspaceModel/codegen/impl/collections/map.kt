package org.jetbrains.deft.collections

import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.Obj
import org.jetbrains.deft.bytes.intBytesCount
import org.jetbrains.deft.impl.ObjStorageImpl

fun <K, V> MutableMap<K, V>.set(other: Map<K, V>) {
    clear()
    putAll(other)
}

inline fun <K, V> Map<K, V>?.outputMaxBytes(
    key: (K) -> Int,
    value: (V) -> Int,
): Int {
    return if (this == null) intBytesCount
    else intBytesCount + entries.sumOf { key(it.key) + value(it.value) }
}

inline fun <K1, K2, V1, V2> Output.writeMapView(
    map: MapView<K1, K2, V1, V2>?,
    key: (K2) -> Unit,
    value: (V2) -> Unit,
) {
    if (map == null) {
        writeInt(0)
    } else {
        writeInt(map.size)
        map.src.entries.forEach {
            key(it.key)
            value(it.value)
        }
    }
}

fun Map<*, *>.updateRefIds() {
    entries.forEach { (k, v) ->
        if (k is WithRefs) k.updateRefIds()
        if (v is WithRefs) v.updateRefIds()
    }
}

fun Map<*, *>.ensureInGraph(value: ObjStorageImpl.ObjGraph?) {
    entries.forEach { (k, v) ->
        if (k is WithRefs) k.ensureInGraph(value)
        if (v is WithRefs) v.ensureInGraph(value)
    }
}

inline fun <K1, K2, V1, V2> Input.readMapView(
    init: () -> MapView<K1, K2, V1, V2>,
    key: () -> K2,
    value: () -> V2,
): MapView<K1, K2, V1, V2>? {
    val size = readInt()
    if (size == 0) return null
    val result = init()
    val k = key()
    val v = value()
    result.src[k] = v
    return result
}