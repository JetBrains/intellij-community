// "Replace with 'NewClass'" "true"

@Deprecated("", ReplaceWith("NewClass"))
class OldClass

class NewClass

fun foo(): List<OldClass<caret>>? {
    return null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix