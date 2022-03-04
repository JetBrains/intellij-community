package org.jetbrains.deft.collections

import org.jetbrains.deft.Obj
import org.jetbrains.deft.OnLink
import org.jetbrains.deft.impl.*

const val initialCapacity = 2

const val MAX_ARRAY_LENGTH = Int.MAX_VALUE - 8

internal fun newLength(oldLength: Int, minGrowth: Int, prefGrowth: Int): Int {
    val newLength = minGrowth.coerceAtLeast(prefGrowth) + oldLength
    return when {
        newLength - MAX_ARRAY_LENGTH <= 0 -> newLength
        else -> hugeLength(oldLength, minGrowth)
    }
}

internal fun hugeLength(oldLength: Int, minGrowth: Int): Int {
    val minLength = oldLength + minGrowth
    if (minLength < 0) throw OutOfMemoryError("Required array length too large")
    return when {
        minLength <= MAX_ARRAY_LENGTH -> MAX_ARRAY_LENGTH
        else -> Int.MAX_VALUE
    }
}
