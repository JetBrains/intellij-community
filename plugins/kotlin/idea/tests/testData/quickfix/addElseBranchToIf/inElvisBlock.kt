// "Add else branch" "true"
fun foo(x: String?) {
    x ?: i<caret>f (x == null) {
        return
    }
}