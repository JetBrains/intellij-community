// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'any{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'result' is never used
fun foo(list: List<String>) {
    var result = 0
    <caret>for (s in list) {
        if (s.length > 0) {
            result = 1
            break
        }
    }
}