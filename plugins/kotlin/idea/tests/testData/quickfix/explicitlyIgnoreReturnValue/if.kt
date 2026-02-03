// "Explicitly ignore return value" "true"
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun functionA(): Int = 42
fun functionB(): String = "42"

fun conditionalExpressionIssues() {
    val condition = functionA() > 30
    if (condition) <caret>functionA() else functionB()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$UnderscoreValueFix