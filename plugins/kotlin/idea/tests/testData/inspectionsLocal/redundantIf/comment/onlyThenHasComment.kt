// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun foo(): Boolean {
    <caret>if (someComplexCondition()) return false // comment explaining the `false` case
    return true
}

fun someComplexCondition(): Boolean = true
