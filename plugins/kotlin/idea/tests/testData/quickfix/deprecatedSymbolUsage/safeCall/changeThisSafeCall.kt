// "Replace with 's.newFun(this)'" "true"
// WITH_STDLIB

class X {
    @Deprecated("", ReplaceWith("s.newFun(this)"))
    fun oldFun(s: String): String = s.newFun(this)
}

fun String.newFun(x: X): String = this

fun foo(x: X?) {
    x?.<caret>oldFun("a")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix