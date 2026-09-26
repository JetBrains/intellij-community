// INTENTION_TEXT: "Add parameter name to all callsites"

fun foo(s<caret>: String = "") {}

fun bar() {
    foo("a")
    foo("b")
    foo(s = "c")
    foo(/* s = */ "d")
    foo()
}