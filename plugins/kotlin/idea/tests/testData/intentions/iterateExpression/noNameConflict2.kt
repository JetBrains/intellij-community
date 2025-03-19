// AFTER-WARNING: Variable 'value1' is never used
// AFTER-WARNING: Variable 'value2' is never used
fun foo(values: List<String>, value: String) {
    if (value == "") {
        val value2 = ""
    }

    values<caret>

    val value1 = ""
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.IterateExpressionIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.IterateExpressionIntention