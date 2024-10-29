// "Replace with 'NewClass'" "true"
package ppp

@Deprecated("", ReplaceWith("NewClass"))
class OldClass<T, V>

class NewClass

fun foo() {
    <caret>OldClass<Int, String>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix