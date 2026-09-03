// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses
// PROBLEM: none

@JvmInline
value class A(val <caret>a: IntArray) {
    operator fun equals(other: A): Boolean {
        if (!a.contentEquals(other.a)) return false

        return true
    }

    override fun hashCode(): Int {
        return a.contentHashCode()
    }
}
