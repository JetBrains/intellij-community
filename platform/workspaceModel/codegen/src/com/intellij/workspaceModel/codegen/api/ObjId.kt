package org.jetbrains.deft

import kotlinx.io.core.Input
import kotlinx.io.core.Output

inline class ObjId<T>(val n: Int) {
    fun next() = ObjId<Any>(n + 1)

    fun isValid() = n > 0
    fun isNothing() = n == nothingN
    fun isNewIdHolder() = n == newIdHolderN

    override fun toString() = when (this) {
        nothing -> "nothing"
        newIdHolder -> "<new>"
        else -> "#$n"
    }

    companion object {
        const val bytesCount: Int = 4

        const val nothingN = 0
        const val newIdHolderN = -1

        /** null pointer */
        val nothing = ObjId<Nothing>(nothingN)

        /** null pointer */
        val newIdHolder = ObjId<Nothing>(newIdHolderN)

        val first = ObjId<Nothing>(1)
    }
}

fun Output.writeId(id: ObjId<*>) {
    writeId(id.n)
}

fun Output.writeId(id: Int) {
    writeInt(id)
}

fun Input.readId(): ObjId<*> {
    val id = readIdValue()
    return ObjId<Any>(id)
}

fun Input.readIdValue(): Int = readInt()