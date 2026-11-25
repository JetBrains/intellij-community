// "Add 'equals(other: Money)' and call it from 'equals(Any?)'" "true"
// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses

@JvmInline
value class Money(val cents: Long) {
    /**
     * Compares the amounts.
     */
    override fun <caret>equals(other: Any?): Boolean {
        // only the amount matters
        if (other !is Money) return false
        return cents == other.cents
    }

    override fun hashCode(): Int = cents.hashCode()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypedEqualsFixFactory$AddTypedEqualsFix
