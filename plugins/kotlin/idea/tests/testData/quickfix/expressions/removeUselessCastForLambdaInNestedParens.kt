// "Remove useless cast" "true"
fun test() {
    ((({ "" } as<caret> () -> String)))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix