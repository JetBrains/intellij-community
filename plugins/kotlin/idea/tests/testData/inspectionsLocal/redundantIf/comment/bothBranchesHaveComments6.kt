// HIGHLIGHT: INFORMATION
fun foo(): Boolean {
    <caret>if (someComplexCondition()) return false // comment explaining the `false` case
    else {
        // comment explaining the `true` case
        return true
    }
}

fun someComplexCondition(): Boolean = true
