// WITH_STDLIB
// FIX: Replace with loop over elements
fun IntArray.arrayToString(): String = buildString {
    for (i in <caret>0 until size) {
        append(this@arrayToString[i])
    }
}