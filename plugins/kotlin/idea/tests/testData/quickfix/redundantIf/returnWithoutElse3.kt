// "Remove redundant 'if' statement" "true"
// WITH_STDLIB
fun test(a: Boolean, b: Boolean): Boolean {
    <caret>if (!a && b) {
        return false
    }

    return true
}