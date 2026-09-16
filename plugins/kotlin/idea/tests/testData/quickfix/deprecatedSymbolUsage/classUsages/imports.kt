// "Replace with 'File'" "true"

@Deprecated("", ReplaceWith("File", "java.io.File"))
class OldClass

fun foo(): OldClass<caret>? {
    return null
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix