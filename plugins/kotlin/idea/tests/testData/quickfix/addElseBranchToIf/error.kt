// "Add else branch" "true"
// ERROR: Unresolved reference: TODO
fun foo(x: String?) {
    val a = i<caret>f (x == null) 4
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddIfElseBranchFix