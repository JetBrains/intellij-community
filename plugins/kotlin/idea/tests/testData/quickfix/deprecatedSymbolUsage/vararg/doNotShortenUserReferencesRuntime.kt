// "Replace with 'newFun(*c)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(*c)"))
fun oldFun(vararg c: Char){}

fun newFun(vararg c: Char){}

fun foo() {
    <caret>oldFun(java.io.File.separatorChar)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix