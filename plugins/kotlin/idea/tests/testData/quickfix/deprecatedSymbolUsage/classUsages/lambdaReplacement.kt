// "Replace with '{ this }'" "false"

package test

@Deprecated("Replace with bar", ReplaceWith("{ this }"))
annotation class Foo

annotation class Bar(val p: Int)

@Foo<caret> class C {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K1