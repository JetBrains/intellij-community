// PROBLEM: none
// WITH_STDLIB

fun foo(a: List<String>, b: List<Int>) {
    a.forEach {
        val <caret>a2 = it
        b.forEach {
            println(a2.length)
        }
    }
}
// IGNORE_K2