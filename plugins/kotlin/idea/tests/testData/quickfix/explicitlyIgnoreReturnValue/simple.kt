// "Explicitly ignore return value" "true"
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun someFunction() {
    <caret>someFunctionValue()
}
fun someFunctionValue(): Int = 1

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$UnderscoreValueFix