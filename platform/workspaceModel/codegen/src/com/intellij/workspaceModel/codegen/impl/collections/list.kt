package org.jetbrains.deft.collections

import com.intellij.workspaceModel.codegen.impl.ObjGraph
import kotlinx.io.core.Output
import org.jetbrains.deft.bytes.intBytesCount
import org.jetbrains.deft.obj.api.collections.assign

@Deprecated(message = "use set from api",
    replaceWith = ReplaceWith("set(other)", "org.jetbrains.deft.obj.api.collections.set")
)
fun <E> MutableList<E>.set(other: List<E>) =
    assign(other)

inline fun <V, T> ListView<V, T>?.outputMaxBytes(
    value: (T) -> Int,
): Int {
    return if (this == null) intBytesCount
    else intBytesCount + srcList.sumOf { value(it) }
}

inline fun <V> Output.writeList(
    list: List<V>?,
    value: (V) -> Unit,
) {
    if (list == null) {
        writeInt(0)
    } else {
        writeInt(list.size)
        list.forEach {
            value(it)
        }
    }
}

fun ListView<*, *>.updateRefIds() {
    srcList.forEach {
        if (it is WithRefs) it.updateRefIds()
    }
}

fun Collection<*>.ensureInGraph(value: ObjGraph?) {
    forEach {
        if (it is WithRefs) it.ensureInGraph(value)
    }
}