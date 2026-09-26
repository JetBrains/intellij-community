// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
fun foo(): Boolean {
    <caret>if (someComplexCondition()) return someOtherCondition() // comment explaining the computed `true` case
    return false
}

fun someComplexCondition(): Boolean = true
fun someOtherCondition(): Boolean = true
