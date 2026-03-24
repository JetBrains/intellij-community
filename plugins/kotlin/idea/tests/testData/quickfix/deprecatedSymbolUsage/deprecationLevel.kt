// "Replace with 'b()'" "true"
// K2_ERROR: 'fun a(): Unit' is deprecated. b!.
@Deprecated("b!", ReplaceWith("b()"), DeprecationLevel.ERROR)
fun a() {}

fun b() {}

fun usage() {
    <caret>a()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix