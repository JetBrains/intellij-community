// "Replace with 'Int::class.java'" "true"
// WITH_STDLIB

@Deprecated("Use class literal", ReplaceWith("T::class.java"))
fun <T> foo() {
}

val x = <caret>foo<Int>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix