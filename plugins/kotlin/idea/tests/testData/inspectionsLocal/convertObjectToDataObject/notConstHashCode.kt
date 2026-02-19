// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
object<caret> Foo {
    override fun toString(): String = "Foo"
    override fun hashCode(): Int = (System.currentTimeMillis() % 2).toInt()
}
