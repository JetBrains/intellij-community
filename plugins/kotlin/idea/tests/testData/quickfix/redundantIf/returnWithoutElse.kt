// "Remove redundant 'if' statement" "true"
fun foo(bar: Int?): Boolean {
    <caret>if (bar == null) { return false }
    return true
}