// "Replace with dot call" "true"
fun Any.foo() {
    this<caret>?.equals(0)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceWithDotCallFix