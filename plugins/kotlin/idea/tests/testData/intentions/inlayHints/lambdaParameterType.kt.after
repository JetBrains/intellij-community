// IS_APPLICABLE: true
// INTENTION_TEXT: "Show function parameter type hints"
// AFTER-WARNING: Parameter 'predicate' is never used
fun String.filter(predicate: (String) -> Boolean): String = this

val q = "".filter { i<caret> ->
    val length = i.length
    length > 0
}