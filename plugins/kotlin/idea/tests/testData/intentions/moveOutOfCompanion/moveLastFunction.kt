class A {
    companion object {
        fun <caret>foo() {

        }
    }

    fun bar() {
        foo()
    }
}