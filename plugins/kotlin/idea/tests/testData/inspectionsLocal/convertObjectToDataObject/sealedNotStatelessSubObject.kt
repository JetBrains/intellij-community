// COMPILER_ARGUMENTS: -XXLanguage:+DataObjects

sealed class Seal(val x: Int)
object<caret> Foo : Seal(1)
