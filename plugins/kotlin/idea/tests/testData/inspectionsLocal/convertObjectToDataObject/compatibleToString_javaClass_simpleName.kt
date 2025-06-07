// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// WITH_STDLIB
object<caret> Foo {
    override fun toString(): String = javaClass.simpleName
}