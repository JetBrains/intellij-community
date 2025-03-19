// "Replace with 'Exception()'" "true"
// RUNTIME_WITH_FULL_JDK
package ppp

@Deprecated("do not use", ReplaceWith("Exception()"))
fun x(): Throwable = RuntimeException()

val e = <caret>x()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix