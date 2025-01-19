// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// WITH_STDLIB
object<caret> Foo {
    override fun toString(): String = "Foo"
    override fun hashCode(): Int = javaClass.hashCode()
}
