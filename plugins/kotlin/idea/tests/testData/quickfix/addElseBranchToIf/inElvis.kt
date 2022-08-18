// "Add else branch" "true"
// ERROR: Unresolved reference: TODO
fun foo(x: String?) {
    x ?: i<caret>f (x == null) return
}