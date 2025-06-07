// "Replace with 'NewClass'" "true"

class Outer {
    @Deprecated("", ReplaceWith("NewClass"))
    class OldClass

    class NewClass
}

fun foo(): Outer.OldClass<caret>? {
    return null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2