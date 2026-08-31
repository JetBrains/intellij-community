// PROBLEM: 'if' expression has identical branches
// FIX: none

fun test(flag: Boolean) {
    if (flag) {
        <caret>println("same")
        // Keep this explanation.
        println("same again")
    } else {
        println("same")
        // Keep this explanation too.
        println("same again")
    }
}
