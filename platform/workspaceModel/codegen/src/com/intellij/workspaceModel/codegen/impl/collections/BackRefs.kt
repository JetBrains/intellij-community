package org.jetbrains.deft.collections

import org.jetbrains.deft.impl.ObjImpl

class BackRefs : Collection<ObjImpl> {
    // todo: var ids: IntArray = IntArray(initialCapacity)
    var refs = arrayOfNulls<ObjImpl>(2)
    var firstNull = Int.MAX_VALUE
    override var size: Int = 0
    var dirtySize = 0

    fun add(src: ObjImpl) {
        requireCapacity(dirtySize + 1)
        refs[dirtySize++] = src
        size++
    }

    fun remove(src: ObjImpl) {
        val i = refs.indexOf(src)
        check(i != -1)
        refs[i] = null
        if (i < firstNull) firstNull = i
        size--
    }

    fun replace(old: ObjImpl, new: ObjImpl) {
        refs.forEachIndexed { i, it ->
            if (it == old) refs[i] = new
        }
    }

    inline fun forEachFast(action: (ObjImpl) -> Unit) {
        refs.forEach {
            if (it != null) action(it)
        }
    }

    private fun requireCapacity(minCapacity: Int) {
        if (dirtySize < minCapacity) {
            clean()
            val oldCapacity: Int = dirtySize
            if (oldCapacity < minCapacity) {
                val newCapacity = newLength(
                    oldCapacity,
                    minCapacity - oldCapacity,
                    oldCapacity shr 1
                )
                refs = refs.copyOf(newCapacity)
            }
        }
    }

    private fun clean() {
        if (firstNull != -1) {
            var i = firstNull
            var j = firstNull + 1
            do {
                val next = refs[j]
                if (next != null) refs[i++] = next
                else j++
            } while (j < dirtySize)
            dirtySize = i
            firstNull = Int.MAX_VALUE
        }
    }

    override fun contains(element: ObjImpl): Boolean =
        refs.contains(element)

    override fun containsAll(elements: Collection<ObjImpl>): Boolean =
        elements.all { refs.contains(it) }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): Iterator<ObjImpl> =
        object : AbstractIterator<ObjImpl>() {
            var i = 0
            override fun computeNext() {
                while (i < dirtySize && refs[i] == null) i++
                if (i == dirtySize) done()
                else setNext(refs[i]!!)
            }
        }
}