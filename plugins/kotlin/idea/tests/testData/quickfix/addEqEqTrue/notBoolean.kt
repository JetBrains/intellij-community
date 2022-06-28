// "Add '== true'" "false"
// ACTION: Do not show return expression hints
// DISABLE-ERRORS
class Foo {
    fun bar() = ""
}

fun test(foo: Foo?) {
    if (foo?.bar()<caret>) {
    }
}
