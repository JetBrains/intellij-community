// LANGUAGE_VERSION: 1.8
// PROBLEM: none

sealed class Seal
object<caret> Foo : Seal() {
    override fun toString() = "FOO"
}
