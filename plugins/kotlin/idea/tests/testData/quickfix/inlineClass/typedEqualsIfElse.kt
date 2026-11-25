// "Add 'equals(other: Money)' and call it from 'equals(Any?)'" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class Money(val cents: Long) {
    override fun <caret>equals(other: Any?): Boolean = if (other is Money) cents == other.cents else false

    override fun hashCode(): Int = cents.hashCode()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypedEqualsFixFactory$AddTypedEqualsFix
