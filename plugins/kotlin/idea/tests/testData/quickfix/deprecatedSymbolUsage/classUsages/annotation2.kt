// "Replace with 'test.Bar'" "true"

package test

@Deprecated("Replace with bar", ReplaceWith("test.Bar"))
annotation class Foo(val p1: String, val p2: Int)

annotation class Bar(val p1: String, val p2: Int)

@Foo<caret>("", 1) class C {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix