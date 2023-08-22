// "Replace with 'x'" "true"

interface X {
    @Deprecated("", ReplaceWith("x"))
    fun getX(): String

    val x: String
}

fun foo(x: X): String {
    return x.<caret>getX()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix