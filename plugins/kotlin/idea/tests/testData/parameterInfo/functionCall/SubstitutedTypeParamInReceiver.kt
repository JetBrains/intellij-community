fun f() {
    listOf(1).foo(<caret>)
}

fun <T> List<T>.foo(t: T) {}

