// "Add 'equals(other: Money)' and call it from 'equals(Any?)'" "false"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class Money(val cents: Long) {
    override fun <caret>equals(other: Any?): Boolean = other is Money && this.equals(other.cents)

    override fun hashCode(): Int = cents.hashCode()
}
