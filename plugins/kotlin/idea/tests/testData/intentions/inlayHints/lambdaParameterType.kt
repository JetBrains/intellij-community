// IS_APPLICABLE: true
// INTENTION_TEXT: "Do not show implicit receiver and parameter hints"
// AFTER-WARNING: Parameter 'predicate' is never used
fun String.filter(predicate: (String) -> Boolean): String = this

val q = "".filter {
    <caret>val length = it.length
    length > 0
}