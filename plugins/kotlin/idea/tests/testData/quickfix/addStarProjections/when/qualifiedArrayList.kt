// "Add '<*>'" "true"
public fun foo(a: Any) {
    when (a) {
        is java.util.Array<caret>List -> {}
        else -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix