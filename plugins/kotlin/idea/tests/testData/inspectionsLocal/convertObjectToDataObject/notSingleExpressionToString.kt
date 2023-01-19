// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
object<caret> Foo {
    override fun toString(): String {
        "foo".hashCode()
        return "Foo"
    }
}
