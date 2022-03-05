package org.jetbrains.deft.collections

import com.intellij.workspaceModel.codegen.impl.ObjGraph
import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.intBytesCount
import org.jetbrains.deft.impl.ObjImpl

fun Refs(owner: ObjImpl, items: Collection<Obj>): Refs {
    val refs = Refs(owner, items.size)
    refs.addAll(items as Collection<ObjImpl>)
    return refs
}

@Suppress("IfThenToElvis")
val Refs?.outputMaxBytes: Int
    get() =
        if (this == null) intBytesCount
        else outputMaxBytes

//fun Output.writeRefs(refs: Refs?) {
//    if (refs == null) {
//        writeInt(0)
//    } else {
//        writeInt(refs.size)
//        refs.forEachId {
//            writeId(it)
//        }
//    }
//}

//fun Input.readRefs(owner: ObjImpl): Refs? {
//    val n = readInt()
//    if (n == 0) return null
//    val refs = Refs(owner, n)
//    repeat(n) {
//        refs.addId(readId())
//    }
//    return refs
//}
//
//fun Input.readChildren(owner: ObjImpl): Children? {
//    val n = readInt()
//    if (n == 0) return null
//
//    val children = Children(owner)
//    repeat(n) {
//        children.addId(readId())
//    }
//    return children
//}

open class Refs(val owner: ObjImpl, _initialCapacity: Int = initialCapacity) : AbstractMutableList<ObjImpl>(),
    ObjectsListBuilder<_Obj0, ObjImpl> {
    var ids: IntArray = IntArray(_initialCapacity)
    var items: Array<ObjImpl?> = arrayOfNulls(_initialCapacity)

    inline fun forEachId(item: (ObjId<*>) -> Unit) {
        repeat(size) {
            item(ObjId<Any>(ids[it]))
        }
    }

    override fun add(element: ObjImpl): Boolean {
        add(size, element)
        return true
    }

    protected open fun onChange(old: ObjImpl?, element: ObjImpl?) {
        owner.onRefUpdate(old, element)
    }

    override var size = 0

    override fun get(index: Int): ObjImpl {
        return owner._getRef(items[index], ObjId<Any>(ids[index]))!!
    }

    fun addId(id: ObjId<*>) {
        val index = size
        incSize()
        items[index] = null
        ids[index] = id.n
    }

    override fun add(index: Int, element: ObjImpl) {
        val initialSize = size
        check(index in 0..initialSize)
        incSize()
        if (index != initialSize) {
            System.arraycopy(items, index, items, index + 1, size - index)
            System.arraycopy(ids, index, ids, index + 1, size - index)
        }
        items[index] = element
        ids[index] = element._id.n

        onChange(null, element)
    }

    private fun incSize(): Int {
        val newSize = size + 1
        if (newSize > items.size) grow(newSize)
        size = newSize
        return newSize
    }

    override fun set(index: Int, element: ObjImpl): ObjImpl {
        check(index in 0..size)
        val old = items[index]!!
        items[index] = element
        ids[index] = element._id.n

        onChange(old, element)

        return old
    }

    override fun removeAt(index: Int): ObjImpl {
        check(index in 0..size)
        val old = items[index]!!

        onChange(old, null)

        val newSize = size--
        if (index != newSize) {
            System.arraycopy(items, index + 1, items, index, newSize - index)
            System.arraycopy(ids, index + 1, ids, index, newSize - index)
        }
        items[newSize] = null
        ids[newSize] = ObjId.nothingN
        size = newSize
        return old
    }

    private fun grow(minCapacity: Int) {
        val oldCapacity: Int = items.size
        val newCapacity = newLength(
            oldCapacity,
            minCapacity - oldCapacity,  /* minimum growth */
            oldCapacity shr 1 /* preferred growth */
        )
        items = items.copyOf(newCapacity)
        ids = ids.copyOf(newCapacity)
    }

    fun replaceWith(value: List<*>) {
        clear()
        addAll(value as Collection<ObjImpl>)
    }

    fun updateRefIds() {
        items.forEachIndexed { index, it ->
            ids[index] = it?._id?.n ?: ObjId.nothingN
        }
    }

    fun ensureInGraph(graph: ObjGraph?) {
        items.forEach {
            it?.ensureInGraph(graph)
        }
    }

    val outputMaxBytes: Int
        get() = intBytesCount + size * ObjId.bytesCount
}