// LANGUAGE_VERSION: 1.8
object<caret> Foo {
    override fun toString(): String = Foo::class.simpleName ?: throw Exception()
}