// "Remove useless cast" "true"
fun foo() {}

fun test() {
    foo()
    // comment
    ((({ "" } as<caret> () -> String)))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix