// "Explicitly ignore return value" "true"
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
// RUN_PIPELINE_TILL: BACKEND
// WITH_STDLIB

fun bar(block: () -> Unit) {
    block()
}

class A

fun testReceivers() {
    val a = A()
    bar(a::toStri<caret>ng)
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$UnderscoreValueFix