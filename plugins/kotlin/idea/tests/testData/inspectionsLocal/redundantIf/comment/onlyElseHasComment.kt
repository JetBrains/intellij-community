// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun foo(): Boolean {
    <caret>if (someComplexCondition()) return true
    return someOtherCondition() // comment explaining the fallback case
}

fun someComplexCondition(): Boolean = true
fun someOtherCondition(): Boolean = true
