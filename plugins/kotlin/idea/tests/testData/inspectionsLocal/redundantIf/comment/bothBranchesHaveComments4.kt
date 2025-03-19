// HIGHLIGHT: INFORMATION
fun foo(): Boolean {
    // comment explaining the `false` case
    <caret>if (someComplexCondition()) {
        return false
    }

    // comment explaining the `true` case
    return true
}

fun someComplexCondition(): Boolean = true