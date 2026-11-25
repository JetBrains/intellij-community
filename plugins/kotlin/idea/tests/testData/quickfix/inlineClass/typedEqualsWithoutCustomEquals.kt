// "Add 'equals(other: Money)' and call it from 'equals(Any?)'" "false"
// WITH_STDLIB
// K2_ERROR: RESERVED_MEMBER_INSIDE_VALUE_CLASS
// K2_AFTER_ERROR: RESERVED_MEMBER_INSIDE_VALUE_CLASS

@JvmInline
value class Money(val cents: Long) {
    override fun <caret>equals(other: Any?): Boolean = other is Money && cents == other.cents
}
