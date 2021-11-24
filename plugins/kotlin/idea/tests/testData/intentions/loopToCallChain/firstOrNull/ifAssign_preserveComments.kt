// WITH_STDLIB
// INTENTION_TEXT: "Replace with 'firstOrNull{}'"
// IS_APPLICABLE_2: false
// AFTER-WARNING: Variable 'result' is never used
fun foo(list: List<String>) {
    var result: String? = null
    <caret>for (s in list) { // search for first non-empty string in the list
        if (s.length > 0) { // string should be non-empty
            result = s // save it into result
            break
        }
    }
}