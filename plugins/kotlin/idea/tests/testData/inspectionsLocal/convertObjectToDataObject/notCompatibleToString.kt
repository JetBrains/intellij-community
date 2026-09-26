// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none
object<caret> Foo {
    override fun toString(): String {
        return "FOO"
    }
}