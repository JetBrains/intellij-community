// "Replace with 'New'" "true"
package ppp

object New

@Deprecated(message = "Deprecated, use New", replaceWith = ReplaceWith("New"))
typealias Old = New

fun main(args: Array<String>) {
    val o = <caret>Old
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix