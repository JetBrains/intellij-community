// PROBLEM: none
// K2-ERROR: Unresolved reference 'foo'.

class JJ () {
    fun bar() {
        <caret>this.foo()
    }

    companion object {
        fun foo() {}
    }
}