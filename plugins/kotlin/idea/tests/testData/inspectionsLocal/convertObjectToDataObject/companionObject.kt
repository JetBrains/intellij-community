// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects

class Foo {
    companion object<caret> Named {
        override fun toString(): String = "Named"
    }
}
