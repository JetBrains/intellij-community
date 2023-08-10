// "Replace with 'bar'" "true"

val bar get() = 1
@Deprecated("use property instead", ReplaceWith("bar"))
fun foo() = 1
fun test(){
    foo<caret>()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix