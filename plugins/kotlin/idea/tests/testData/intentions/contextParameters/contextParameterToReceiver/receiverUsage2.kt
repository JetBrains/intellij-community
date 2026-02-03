// COMPILER_ARGUMENTS: -Xcontext-parameters

context(<caret>c1: String, c2: Int)
fun foo(p1: Double) {
}

fun String.bar() {
    fun Int.baz() {
        foo(2.0)
    }
}
