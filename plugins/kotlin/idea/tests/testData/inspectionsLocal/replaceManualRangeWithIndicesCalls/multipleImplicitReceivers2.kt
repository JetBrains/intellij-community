// WITH_STDLIB
fun LongArray.arrayToString(): String = buildString {
    for (i in <caret>0 until size) {
        if (i > 0) append(", ")
        append(this@arrayToString[i])
    }
}