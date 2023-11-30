// "Replace with 'newFun(this)'" "true"
// ERROR: Unresolved reference: @Outer

class Outer {
    inner class Inner {
        @Deprecated("", ReplaceWith("newFun(this)"))
        fun oldFun() {}
    }

    fun newFun(inner: Inner) {}
}

fun foo(inner: Outer.Inner) {
    inner.<caret>oldFun()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix