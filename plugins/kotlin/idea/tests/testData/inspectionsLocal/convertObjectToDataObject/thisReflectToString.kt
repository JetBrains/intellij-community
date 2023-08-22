// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
object<caret> Foo {
    override fun toString(): String = this::class.simpleName ?: throw Exception()
}
