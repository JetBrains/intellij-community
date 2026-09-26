// PROBLEM: 'if' expression has identical branches
// FIX: Collapse 'if' expression

private fun run(block: () -> Unit) {}

fun test(flag: Boolean) {
    if (flag) {
        <caret>val value = 42
        println(value)
    } else {
        val value = 42
        println(value)
    }
}
