interface I<T> {
    fun foo(t: T): (item: T) -> Unit{}
}

fun f(i: I<String>) {
    val v = i.foo()
    v(<caret>)
}
