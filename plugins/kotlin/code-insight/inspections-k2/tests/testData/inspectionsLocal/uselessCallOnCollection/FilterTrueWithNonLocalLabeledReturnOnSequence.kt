// WITH_STDLIB
// K2_ERROR: RETURN_NOT_ALLOWED
// PROBLEM: none

fun f(): Boolean {
    val someList = sequenceOf("alpha", "beta").<caret>filter { return@f true }
    return true
}