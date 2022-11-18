// "Remove redundant 'if' statement" "true"
fun foo(bar: Int?): Boolean {
    if (bar == 3) { return true }
    <caret>if (bar == null) { return false }
    // A comment

    /**
     * And more comment
     */

    return true
    bar?.let{ it + 4 }
}