// "Replace with 'B<Int>'" "true"

@Deprecated("", ReplaceWith("B<Int>"))
class C<T, F>

class B<T>

fun foo() {
    var c: <caret>C<Int, String>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix