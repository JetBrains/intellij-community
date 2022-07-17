// "Replace with 'newFun(this, value)'" "true"
class Foo {
    @Deprecated(message = "", replaceWith = ReplaceWith("newFun(this, value)"))
    fun String.oldFun(value: String) {}

    fun newFun(key: String, value: String) {}
}

fun foo(init: Foo.() -> Unit) {}

fun test() {
    foo {
        "a".<caret>oldFun("b")
    }
}
