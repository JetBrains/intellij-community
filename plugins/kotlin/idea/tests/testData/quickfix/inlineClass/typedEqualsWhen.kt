// "Add 'equals(other: Money)' and call it from 'equals(Any?)'" "false"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class Money(val cents: Long) {
    override fun <caret>equals(other: Any?): Boolean = when (other) {
        is Money -> cents == other.cents
        is Long -> cents == other
        else -> false
    }

    override fun hashCode(): Int = cents.hashCode()
}
