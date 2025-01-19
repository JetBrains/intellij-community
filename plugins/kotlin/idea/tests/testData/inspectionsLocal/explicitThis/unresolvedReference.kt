// PROBLEM: none
// ERROR: Unresolved reference: foo
// K2-ERROR: Unresolved reference 'foo'.

class JJ () {
    fun bar() {
        <caret>this.foo()
    }

    companion object {
        fun foo() {}
    }
}