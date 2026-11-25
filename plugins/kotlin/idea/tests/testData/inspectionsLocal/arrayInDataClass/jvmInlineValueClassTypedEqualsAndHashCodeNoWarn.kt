// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses
// PROBLEM: none

@JvmInline
value class A(val <caret>a: IntArray) {
    fun equals(other: A): Boolean = a.contentEquals(other.a)

    override fun hashCode(): Int = a.contentHashCode()
}
