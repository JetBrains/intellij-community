// "Remove redundant 'if' statement" "true"
fun test(a: Boolean, b: Boolean): Boolean {
    <caret>if (!a && b) {
        // comment1
        // comment2
        return false
    }

    return true
}