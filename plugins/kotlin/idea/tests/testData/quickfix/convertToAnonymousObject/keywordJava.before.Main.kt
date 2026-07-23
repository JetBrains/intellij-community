// "Convert to anonymous object" "true"
// K2_ERROR: INTERFACE_AS_FUNCTION

fun test() {
    <caret>KeywordSam { _ -> }
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ConvertToAnonymousObjectFixFactories$ConvertToAnonymousObjectFix
