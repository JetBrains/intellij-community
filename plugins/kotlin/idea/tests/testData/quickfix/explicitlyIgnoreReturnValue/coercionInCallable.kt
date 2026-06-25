// "Explicitly ignore return value" "true"
// LANGUAGE_VERSION: 2.2
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
// RUN_PIPELINE_TILL: BACKEND
// WITH_STDLIB

fun foo(): String = ""
fun bar(block: () -> Unit) {
    block()
}

fun test() {
    bar { foo() }
}

fun testRefs() {
    bar(::f<caret>oo)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$UnderscoreValueFix