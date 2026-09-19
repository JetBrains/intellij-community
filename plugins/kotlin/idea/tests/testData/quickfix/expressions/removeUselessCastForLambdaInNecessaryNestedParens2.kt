// "Remove redundant cast" "true"
fun foo() {}

fun test() {
    foo()
    // comment
    ((({ "" } as<caret> () -> String)))
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessCastFix