// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
object<caret> Foo {
    override fun toString(): String = "Foo"
    override fun equals(other: Any?): Boolean = other is Foo
}
