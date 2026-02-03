// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'any{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'found' is never used
fun foo(list: List<String>) {
    var found = false
    <caret>for (s in list) {
        if (s.length > 0) {
            found = true
            break
        }
    }
}