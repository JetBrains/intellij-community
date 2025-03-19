// WITH_STDLIB
fun IntArray.arrayToString(): String = buildString {
    for (i in <caret>0 until size) {
        append(this@arrayToString[i])
    }
}