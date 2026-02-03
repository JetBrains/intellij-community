// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects

sealed class Seal
object<caret> Foo : Seal() {
    override fun toString() = "Foo"
}
