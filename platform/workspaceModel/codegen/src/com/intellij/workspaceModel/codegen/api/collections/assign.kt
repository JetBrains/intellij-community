package org.jetbrains.deft.obj.api.collections

fun <E> MutableList<E>.assign(other: Collection<E>) {
    clear()
    addAll(other)
}

inline fun <E> MutableList<E>.assign(other: Collection<E>, transform: (E) -> E) {
    if (this == other) {
        var i = 0
        val n = other.size
        while (i < n) {
            this[i] = transform(this[i])
            i++
        }
    } else {
        clear()
        other.mapTo(this, transform)
    }
}