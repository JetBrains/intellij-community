// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none

open class Base {
    override fun equals(other: Any?): Boolean = other is Foo
}

object<caret> Foo : Base() {
    override fun toString(): String = "Foo"
}
