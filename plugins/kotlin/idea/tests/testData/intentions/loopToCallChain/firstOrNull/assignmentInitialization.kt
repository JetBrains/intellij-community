// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'firstOrNull{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: The value 'list.firstOrNull { it.length > 0 }' assigned to 'var result: String? defined in foo' is never used
// AFTER-WARNING: Variable 'result' is assigned but never accessed
fun foo(list: List<String>) {
    var result: String? = ""

    result = null
    <caret>for (s in list) {
        if (s.length > 0) {
            result = s
            break
        }
    }
}