// PROBLEM: none
// WITH_RUNTIME
fun test() {
    val a: Int? by lazy { null }
    val <caret>b = a
    if (b != null) {
        foo(b)
    }
}

fun foo(i: Int) {}