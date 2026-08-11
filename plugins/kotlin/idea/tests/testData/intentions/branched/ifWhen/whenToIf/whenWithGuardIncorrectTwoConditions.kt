// WITH_STDLIB
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: COMMA_IN_WHEN_CONDITION_WITH_WHEN_GUARD

// COMPILER_ARGUMENTS: -Xwhen-guards

private fun test(s: Any) {
    when (s) {
        is String,
        is Int <caret>if s > 5 -> { println("1") }
        else -> { println("2") }
    }
}
