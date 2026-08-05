// WITH_STDLIB
// K2_ERROR:
// PROBLEM: none

val someList = sequenceOf("alpha", "beta").<caret>filter {
    if (it.isEmpty()) return@filter false

    true
}