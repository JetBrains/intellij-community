// "Replace with 'B'" "true"
package a
@Deprecated("", ReplaceWith("B"))
class A

class B

fun foo(a: a.<caret>A) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix