// "Remove useless cast" "true"
fun test(x: Any): Int {
    if (x is String) {
        return (((x <caret>as String))).length
    }
    return -1
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix