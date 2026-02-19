// PROBLEM: none

class Foo {
    val otherInstance: Foo
        get() = null!!

    val p: Any
        get() {
            val v = this
            return v.p<caret>
        }
}
