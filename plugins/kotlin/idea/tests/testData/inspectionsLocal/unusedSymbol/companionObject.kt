// PROBLEM: none
class A {
    private <caret>companion object {
        const val FOO = "foo"
    }

    fun foo() {
        val f = FOO
    }
}