fun f() {
    listOf(1).foo<<caret>>()
}

fun <T, K> List<T>.foo() {}

