// "Replace with 'Some'" "true"

class Some

@Deprecated("Use Some instead", replaceWith = ReplaceWith("Some"))
typealias A = Some

val a: <caret>A = A()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix