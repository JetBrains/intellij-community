// "Add return keyword" "true"
// LANGUAGE_VERSION: 2.3
// COMPILER_ARGUMENTS: -Xreturn-value-checker=full
fun greeting(): String = "Hello"

fun test(): String {
    try {
        <caret>greeting()
    }
    catch (e: Exception) {
        return "Error"
    }
    return "Done"
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.NoReturnValueFactory$AddReturnKeywordFix
