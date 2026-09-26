fun <T, V> foo(t: T): T = t

fun bar() {
    foo<<caret>>()
}

