// "Replace with 'java.util.Random'" "true"

@Deprecated("", ReplaceWith("java.util.Random"))
class OldClass

fun foo() {
    <caret>OldClass(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix