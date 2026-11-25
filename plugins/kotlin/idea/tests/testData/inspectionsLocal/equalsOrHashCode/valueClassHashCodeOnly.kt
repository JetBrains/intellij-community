// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class <caret>Money(val cents: Long) {
    override fun hashCode(): Int = cents.hashCode()
}
