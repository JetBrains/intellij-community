// PROBLEM: 'toString()' called on array
// FIX: Replace with 'contentToString()'

// WITH_STDLIB

fun printItems(vararg items: Int) {
    val s = items.<caret>toString()
    println(s)
}
