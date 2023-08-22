// "Replace with 'B'" "true"

@Deprecated("", ReplaceWith("B"))
class C<T, F>

class B

fun foo() {
    var c: <caret>C<Int, String>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix