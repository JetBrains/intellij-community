// INTENTION_TEXT: "Add parameter name to all callsites"

enum class Foo(i: Int) {
    Foo1("a"),
    Foo2("b"),
    Foo3(s = "c"),
    Foo4(/* s = */ "d"),
    Foo5(),
    ;
    constructor(s<caret>: String = "") : this(1)
}