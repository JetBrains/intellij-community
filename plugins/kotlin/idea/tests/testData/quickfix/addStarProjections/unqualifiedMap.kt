// "Add '<*, *>'" "true"
// K2_ERROR: 2 type arguments expected. Use 'Map<*, *>' if you do not intend to pass type arguments.
public fun foo(a: Any) {
    a is Map<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix