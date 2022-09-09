// LANGUAGE_VERSION: 1.8
// PROBLEM: none
object<caret> Foo {
    override fun toString(): String = "Foo"
    override fun hashCode(): Int = (System.currentTimeMillis() % 2).toInt()
}
