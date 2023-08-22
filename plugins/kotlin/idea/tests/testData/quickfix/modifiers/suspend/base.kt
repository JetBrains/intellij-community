// "Make bar suspend" "true"

suspend fun foo() {}
fun bar() {
    <caret>foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix