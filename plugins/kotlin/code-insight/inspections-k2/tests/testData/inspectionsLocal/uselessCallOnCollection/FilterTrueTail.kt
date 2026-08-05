// WITH_STDLIB
// PROBLEM: none

val someList = listOf("alpha", "beta").<caret>filter {
    if (it.isEmpty()) return@filter false

    true
}