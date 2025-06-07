// WITH_STDLIB
// PROBLEM: none

class Foo {
    val otherInstance: Foo
        get() = null!!

    val p: Any = with(otherInstance) { p<caret> }
}
