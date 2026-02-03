// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects

open class Base {
    open protected fun foo() = "Base"
}

object<caret> Foo : Base() {
    override fun toString(): String = "Foo"
}
