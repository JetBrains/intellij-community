// "Add else branch" "true"
fun foo(x: String?) {
    x ?: i<caret>f (x == null) {
        return
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIfElseBranchFix