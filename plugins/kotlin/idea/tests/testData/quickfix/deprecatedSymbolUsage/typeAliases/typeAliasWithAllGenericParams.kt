// "Replace with 'X<Int>'" "true"

package ppp

class X<T>

@Deprecated("Will be dropped", replaceWith = ReplaceWith("X<Int>", "ppp.X"))
typealias IntX = X<Int>

fun foo(ix: <caret>IntX) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix