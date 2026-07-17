// "class org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix" "false"
// ERROR: 2 type arguments expected for interface Map<K, out V>
// K2_AFTER_ERROR: WRONG_NUMBER_OF_TYPE_ARGUMENTS
// K2_ERROR: WRONG_NUMBER_OF_TYPE_ARGUMENTS
public fun foo(a: Any) {
    a is <caret>Map<Int>
}
