inline fun <reified T> foo(p: T) {}

fun bar() {
    foo<<caret>>
}

