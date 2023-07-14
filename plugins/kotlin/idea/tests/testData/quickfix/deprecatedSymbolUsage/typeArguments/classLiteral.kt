// "Replace with 'String::class'" "true"

@Deprecated("Use class literal", ReplaceWith("T::class"))
fun <T> foo() {
}

val x = <caret>foo<String>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix