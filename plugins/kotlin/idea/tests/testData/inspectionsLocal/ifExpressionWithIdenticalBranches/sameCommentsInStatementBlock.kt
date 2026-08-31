// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean) {
    if (flag) {
        <caret>println("same")
        // Keep this explanation.
        println("same again")
    } else {
        println("same")
        // Keep this explanation.
        println("same again")
    }
}
