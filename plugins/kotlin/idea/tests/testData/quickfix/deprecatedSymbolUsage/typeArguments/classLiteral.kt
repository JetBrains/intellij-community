// "Replace with 'String::class'" "true"
// K2_ACTION: "Replace with 'T::class'" "true"

@Deprecated("Use class literal", ReplaceWith("T::class"))
fun <T> foo() {
}

val x = <caret>foo<String>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix