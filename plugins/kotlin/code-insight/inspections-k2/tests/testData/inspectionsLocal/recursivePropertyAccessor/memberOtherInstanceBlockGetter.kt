// PROBLEM: none

class Foo {
    val otherInstance: Foo
        get() = null!!

    val p: Any
        get() {
            return otherInstance.p<caret>
        }
}
