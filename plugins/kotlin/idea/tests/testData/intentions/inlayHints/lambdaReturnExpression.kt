// IS_APPLICABLE: true
// INTENTION_TEXT: "Do not show return expression hints"
// AFTER-WARNING: Parameter 'predicate' is never used
fun String.filter(predicate: (String) -> Boolean): String = this

val q = "".filter {
    val length = it.length
    <caret>length > 0
}