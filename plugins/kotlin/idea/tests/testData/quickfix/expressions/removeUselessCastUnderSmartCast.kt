// "Remove useless cast" "true"
fun test(x: Any): String? {
    if (x is String) {
        return x <caret>as String
    }
    return null
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix