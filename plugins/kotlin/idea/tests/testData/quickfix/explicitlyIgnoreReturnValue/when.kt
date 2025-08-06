// "Explicitly ignore return value" "true"
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun returnsInt(): Int = 42
fun returnsString(): String = "hello"
fun returnsNullable(): String? = null

fun mixedTest() {
    when (returnsInt()) {
        1 -> returnsString()<caret>
        else -> returnsNullable()
    }
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$UnderscoreValueFix