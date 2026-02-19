// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'
// IGNORE_K1
// WITH_STDLIB

fun printItems(vararg items: Int) {
    val s = items.<caret>toString()
    println(s)
}
