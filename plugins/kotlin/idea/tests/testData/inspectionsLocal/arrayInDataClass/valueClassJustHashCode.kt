// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class A(val <caret>a: IntArray) {
    override fun hashCode(): Int {
        return a.contentHashCode()
    }
}
