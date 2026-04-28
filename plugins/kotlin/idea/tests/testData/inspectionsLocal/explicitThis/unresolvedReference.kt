// PROBLEM: none
// ERROR: Unresolved reference: foo
// K2_ERROR: Unresolved reference 'foo' on receiver of type 'JJ'.

class JJ () {
    fun bar() {
        <caret>this.foo()
    }

    companion object {
        fun foo() {}
    }
}