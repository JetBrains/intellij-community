// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class A {
    class B {
       fun <caret>length(): Int = 1
    }
}

fun m(ab: A.B) {
    val l = ab.length()
}