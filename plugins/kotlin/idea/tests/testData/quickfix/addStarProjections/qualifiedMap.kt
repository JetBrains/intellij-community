// "Add '<*, *>'" "true"
public fun foo(a: Any) {
    a is kotlin.collections.Map<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix