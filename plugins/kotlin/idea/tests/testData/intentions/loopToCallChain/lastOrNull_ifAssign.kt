// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'lastOrNull{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'result' is never used
fun foo(list: List<String>) {
    var result: String? = null
    <caret>for (s in list) {
        if (s.length > 0) {
            result = s
        }
    }
}