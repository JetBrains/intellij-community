// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'indexOfFirst{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'result' is never used
fun foo(list: List<String>) {
    var result = -1
    <caret>for ((index, s) in list.withIndex()) {
        if (s.length > 0) {
            result = index
            break
        }
    }
}