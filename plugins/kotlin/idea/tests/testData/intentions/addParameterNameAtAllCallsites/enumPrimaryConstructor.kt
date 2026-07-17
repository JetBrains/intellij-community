// INTENTION_TEXT: "Add parameter name to all callsites"

enum class Foo(s<caret>: String = "") {
    Foo1("a"),
    Foo2("b"),
    Foo3(s = "c"),
    Foo4(/* s = */ "d"),
    Foo5(),
}