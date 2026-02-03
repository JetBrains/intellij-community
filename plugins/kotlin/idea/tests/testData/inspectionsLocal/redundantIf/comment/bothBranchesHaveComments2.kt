// HIGHLIGHT: INFORMATION
fun foo(): Boolean {
    <caret>if (someComplexCondition()) return false // comment explaining the `false` case
    else return true // comment explaining the `true` case
}

fun someComplexCondition(): Boolean = true
