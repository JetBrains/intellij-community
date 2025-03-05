// "Replace with '1'" "true"

fun foo(): Int {
    @Deprecated("", ReplaceWith("1"))
    fun localFun() = 1

    return <caret>localFun()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2