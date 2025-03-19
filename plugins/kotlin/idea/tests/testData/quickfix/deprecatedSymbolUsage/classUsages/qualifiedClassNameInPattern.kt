// "Replace with 'java.io.File'" "true"

@Deprecated("", ReplaceWith("java.io.File"))
class OldClass

fun foo(): OldClass<caret>? {
    return null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix