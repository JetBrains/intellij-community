// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

fun test(flag: Boolean) {
    if (flag) {
        <caret>val value = 42
        println(value)
    } else {
        val value = 42
        println(value)
    }
    val value = "outside"
    println(value)
}
