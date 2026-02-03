// PROBLEM: none
class Foo {
    fun foo() {
        object {
            private val x = "foo"

            <caret>inner class Foo {
                fun foo() {
                    x
                }
            }
        }
    }
}