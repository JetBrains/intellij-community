// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+FullValueClasses

value class A(val <caret>a: IntArray) {
    override fun hashCode(): Int {
        return a.contentHashCode()
    }
}
