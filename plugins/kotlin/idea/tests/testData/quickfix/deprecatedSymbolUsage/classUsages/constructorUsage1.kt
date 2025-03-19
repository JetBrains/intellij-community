// "Replace with 'NewClass'" "true"

@Deprecated("", ReplaceWith("NewClass"))
class OldClass(p: Int)

class NewClass(p: Int)

fun foo() {
    <caret>OldClass(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix