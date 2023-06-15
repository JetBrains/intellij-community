// "Remove redundant 'if' statement" "true"
// WITH_STDLIB
fun foo(b: Boolean): Boolean {
    <caret>if (b) return false // comment

    return true
}