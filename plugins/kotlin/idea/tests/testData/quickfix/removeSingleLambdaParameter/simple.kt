// "Remove single lambda parameter declaration" "true"
// WITH_STDLIB

fun main() {
    listOf(1).forEach { <caret>x -> println() }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSingleLambdaParameterFix