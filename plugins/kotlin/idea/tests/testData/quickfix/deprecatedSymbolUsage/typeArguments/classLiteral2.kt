// "Replace with 'Int::class.java'" "true"
// K2_ACTION: "Replace with 'T::class.java'" "true"
// WITH_STDLIB

@Deprecated("Use class literal", ReplaceWith("T::class.java"))
fun <T> foo() {
}

val x = <caret>foo<Int>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix