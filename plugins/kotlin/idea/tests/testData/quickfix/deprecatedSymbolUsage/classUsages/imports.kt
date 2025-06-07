// "Replace with 'File'" "true"

@Deprecated("", ReplaceWith("File", "java.io.File"))
class OldClass

fun foo(): OldClass<caret>? {
    return null
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2