// PROBLEM: 'if' expression has identical branches
// FIX: none

fun test(flag: Boolean) {
    while (true) {
        if (flag) {
            <caret>val value = 42
            println(value)
            break
        } else {
            val value = 42
            println(value)
            break
        }
    }
}
