// "Add else branch" "true"
// ERROR: Unresolved reference: TODO
// K2_ERROR: INVALID_IF_AS_EXPRESSION
fun foo(x: String?) {
    val a = i<caret>f (x == null) 4
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIfElseBranchFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIfElseBranchFix