// "Add else branch" "true"
fun foo(x: String?) {
    val a = i<caret>f (x == null) {
        4
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIfElseBranchFix