// PROBLEM: none
// WITH_STDLIB
fun test(flag: Boolean): Int {
    return <caret>if (!flag) throw IllegalArgumentException() else 1
}

