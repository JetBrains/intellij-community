// "Add non-null asserted (map[3]!!) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_OPERATOR_CALL
fun test(map: MutableMap<Int, Int>) {
    map[3] +=<caret> 5
}

// IGNORE_K2


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix