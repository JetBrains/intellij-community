// "Add 'inline' to function 'foo'" "true"

fun foo(<caret>crossinline body: () -> Unit) {

}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddInlineToFunctionFix