// LANGUAGE_VERSION: 1.8
object<caret> Foo {
    override fun toString(): String = "Foo"
    override fun equals(other: Any?): Boolean = other is Foo
}
