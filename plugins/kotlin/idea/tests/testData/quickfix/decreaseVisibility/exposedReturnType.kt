// "Make 'bar' private" "true"
// PRIORITY: HIGH
// ACTION: Convert member to extension
// ACTION: Convert to block body
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Make 'Data' internal
// ACTION: Make 'Data' public
// ACTION: Make 'bar' private
// ACTION: Move to companion object
// ACTION: Specify return type explicitly

private data class Data(val x: Int)

class First {
    internal fun <caret>bar(x: Int) = Data(x)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPrivateFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPrivateModCommandAction