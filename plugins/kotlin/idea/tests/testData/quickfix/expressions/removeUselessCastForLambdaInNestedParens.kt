// "Remove redundant cast" "true"
fun test() {
    ((({ "" } as<caret> () -> String)))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix