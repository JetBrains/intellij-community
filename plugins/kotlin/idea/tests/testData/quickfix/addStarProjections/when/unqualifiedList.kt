// "Add '<*>'" "true"
public fun foo(a: Any) {
    when (a) {
        is List<caret> -> {}
        else -> {}
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddStarProjectionsFix