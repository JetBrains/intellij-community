// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

fun test(n: Int) {
    <caret>foo = n + 1
}