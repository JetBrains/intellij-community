// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
object<caret> Foo {
    override fun toString(): String = "Foo"
    fun readResolve(): Any = Foo
}
