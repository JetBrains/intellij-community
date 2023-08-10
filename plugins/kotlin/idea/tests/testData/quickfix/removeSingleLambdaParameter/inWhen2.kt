// "Remove single lambda parameter declaration" "true"
fun test(i: Int) {
    val p: (String) -> Boolean =
        when (i) {
            1 -> { { <caret>s -> true } }
            else -> { s -> false }
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSingleLambdaParameterFix