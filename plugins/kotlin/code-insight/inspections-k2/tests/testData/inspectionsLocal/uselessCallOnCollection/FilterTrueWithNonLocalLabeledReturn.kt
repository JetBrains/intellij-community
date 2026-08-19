// WITH_STDLIB
// PROBLEM: none

fun f(): Boolean {
    val someList = listOf("alpha", "beta").<caret>filter { return@f true }
    return true
}