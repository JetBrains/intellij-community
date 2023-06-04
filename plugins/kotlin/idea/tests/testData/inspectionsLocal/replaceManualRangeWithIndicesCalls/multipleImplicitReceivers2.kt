// WITH_STDLIB
private fun LongArray.arrayToString(): String = buildString {
    append('[')
    for (i in <caret>0 until size) {
        if (i > 0)
            append(", ")
        append(this@arrayToString[i])
    }
    append(']')
}