// PROBLEM: none

class Foo {
    val otherInstance: Foo
        get() = null!!

    val p: Any = otherInstance.p<caret>
}
