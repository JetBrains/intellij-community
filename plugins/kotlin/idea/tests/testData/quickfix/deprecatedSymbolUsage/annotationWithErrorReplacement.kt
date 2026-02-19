// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix" "false"

@Deprecated("", replaceWith = ReplaceWith("()"))
annotation class Foo

@Fo<caret>o
class Bar