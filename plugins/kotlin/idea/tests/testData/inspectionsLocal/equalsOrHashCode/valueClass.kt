// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses
// IGNORE_K1
@JvmInline
value class <caret>Money(val cents: Long) {
    override fun hashCode(): Int = cents.hashCode()

    public operator fun equals(other: Money): Boolean {
        return cents == other.cents
    }
}