// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

value class A(val <caret>a: IntArray) {
    fun equals(other: A): Boolean {
        if (!a.contentEquals(other.a)) return false

        return true
    }
}
