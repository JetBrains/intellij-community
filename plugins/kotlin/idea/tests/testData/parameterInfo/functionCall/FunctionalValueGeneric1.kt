fun <T> T.foo(): (item: T) -> Unit{}

fun f() {
    val v = "a".foo()
    v(<caret>)
}

