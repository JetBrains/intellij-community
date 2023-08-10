// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'contains()'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'v' is never used
fun foo(list: List<String>) {
    var v = true
    <caret>for (s in list) {
        if (s == "a") {
            v = false
            break
        }
    }
}