// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
object<caret> Foo {
    override fun toString(): String {
        return "Foo"
    }

    init {
        // some code
    }
}
