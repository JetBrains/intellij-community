// LANGUAGE_VERSION: 1.6

fun test() {
    foo(false || !true) + foo(<caret>false || !true)
}
fun foo(v: Boolean): Int = 1