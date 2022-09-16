// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects
// PROBLEM: none

sealed class Seal
object<caret> Foo : Seal() {
    override fun toString() = "FOO"
}
