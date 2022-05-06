// "Create property 'foo'" "false"
// ACTION: Do not show return expression hints
// ERROR: Unresolved reference: @foo
fun refer() {
    val v = this@<caret>foo
}