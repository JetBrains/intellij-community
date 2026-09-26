// WITH_STDLIB
// PROBLEM: none

val someList = listOf("alpha", "beta").<caret>filter {
    if (it in listOf("x", "y")) return@filter true
    false
}