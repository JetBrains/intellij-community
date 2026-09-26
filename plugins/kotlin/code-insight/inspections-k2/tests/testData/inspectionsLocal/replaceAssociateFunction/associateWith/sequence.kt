// PROBLEM: Replace 'associate' with 'associateWith'
// FIX: Replace with 'associateWith'
// WITH_STDLIB
fun getValue(i: Int): String = ""

fun test() {
    sequenceOf(1).<caret>associate { it to getValue(it) }
}