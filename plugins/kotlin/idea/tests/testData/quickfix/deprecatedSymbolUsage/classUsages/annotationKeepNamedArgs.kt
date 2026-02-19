// "Replace with 'Bar(p, "")'" "true"

package test

@Deprecated("Replace with bar", ReplaceWith("Bar(p, \"\")"))
annotation class Foo(val p: Int)

annotation class Bar(val p: Int, val s: String)

@Foo<caret>(p = 1) class C
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix