// INTENTION_TEXT: "Add parameter name to all callsites"

class Foo {
    constructor(s<caret>: String = "")
}

fun bar() {
    Foo("a")
    Foo("b")
    Foo(s = "c")
    Foo(/* s = */ "d")
    Foo()
}