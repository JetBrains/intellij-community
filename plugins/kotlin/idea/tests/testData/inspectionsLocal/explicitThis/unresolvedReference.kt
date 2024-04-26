// PROBLEM: none
class JJ () {
    fun bar() {
        <caret>this.foo()
    }

    companion object {
        fun foo() {}
    }
}