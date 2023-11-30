// HIGHLIGHT: INFORMATION
fun foo(): Boolean {
    <caret>if (someComplexCondition()) {
        // comment explaining the `false` case
        // comment explaining the `false` case
        return false
    }

    // comment explaining the `true` case
    return true
}

fun someComplexCondition(): Boolean = true