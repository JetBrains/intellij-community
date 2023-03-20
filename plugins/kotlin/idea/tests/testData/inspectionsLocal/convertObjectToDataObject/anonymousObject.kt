// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects

class Foo {
    fun main() {
        val x = <caret>object {
            override fun toString(): String = "Foo"
        }
    }
}
