// PROBLEM: none
// ERROR: Unresolved reference: foo
// K2_ERROR: UNRESOLVED_REFERENCE

class JJ () {
    fun bar() {
        <caret>this.foo()
    }

    companion object {
        fun foo() {}
    }
}