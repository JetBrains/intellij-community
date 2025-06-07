// "Replace with 'BAR'" "true"

enum class Enm {
    @Deprecated("Replace with BAR", ReplaceWith("BAR"))
    FOO,
    BAR
}

fun test() {
    Enm.FOO<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2