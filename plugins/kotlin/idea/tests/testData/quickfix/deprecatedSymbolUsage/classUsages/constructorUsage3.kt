// "Replace with 'NewClass'" "true"
package ppp

@Deprecated("", ReplaceWith("NewClass"))
class OldClass(p: Int)

class NewClass(p: Int)

fun foo() {
    ppp.<caret>OldClass(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix