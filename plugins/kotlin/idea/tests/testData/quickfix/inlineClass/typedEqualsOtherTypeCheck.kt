// "Add 'equals(other: Money)' and call it from 'equals(Any?)'" "false"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class Money(val cents: Long) {
    override fun <caret>equals(other: Any?): Boolean = other is Money && cents == other.cents || other is Long && cents == other

    override fun hashCode(): Int = cents.hashCode()
}
