// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean) {
    val localFlag = flag
    if (localFlag) {
        <caret>println("same")
        println("same again")
    } else {
        println("same")
        println("same again")
    }
}
