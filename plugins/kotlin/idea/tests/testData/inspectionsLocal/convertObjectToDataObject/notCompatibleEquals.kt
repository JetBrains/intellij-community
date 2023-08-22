// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
object<caret> Foo {
    override fun toString(): String = "Foo"
    override fun equals(other: Any?): Boolean = other is Any
}
