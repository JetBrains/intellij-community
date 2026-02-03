// "Add '== true'" "false"
// DISABLE_ERRORS
class Foo {
    fun bar() = ""
}

fun test(foo: Foo?) {
    if (foo?.bar()<caret>) {
    }
}
