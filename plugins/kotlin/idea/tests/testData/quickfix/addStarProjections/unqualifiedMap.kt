// "Add '<*, *>'" "true"
public fun foo(a: Any) {
    a is Map<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix