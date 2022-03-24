// "Replace with 'new'" "true"
// WITH_STDLIB

class A {
    @Deprecated("msg", ReplaceWith("new"))
    var old = ""
        get() = new
        public set

    var new = ""
}

fun foo() {
    val a = A()
    a.<caret>old = "foo"
}