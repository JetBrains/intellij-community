// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun foo(): Boolean {
    <caret>if (someComplexCondition()) return false
    return true // comment explaining the `true` case
}

fun someComplexCondition(): Boolean = true
