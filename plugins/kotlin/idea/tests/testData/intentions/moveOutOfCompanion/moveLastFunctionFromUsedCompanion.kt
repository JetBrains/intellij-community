class A {
    companion object {
        fun <caret>foo() {

        }
    }

    fun companion(): Any = Companion

    fun bar() {
        foo()
    }
}