// "Remove redundant cast" "true"
fun foo(a: Any) {
    val b = a <caret>as Any
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix