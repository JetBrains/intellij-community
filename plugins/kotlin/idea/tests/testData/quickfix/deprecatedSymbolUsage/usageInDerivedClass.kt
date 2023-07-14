// "Replace with 'newFun(s)'" "true"

open class Base {
    @Deprecated("", ReplaceWith("newFun(s)"))
    fun oldFun(s: String){}

    fun newFun(s: String){}
}

class Derived : Base() {
    fun foo() {
        <caret>oldFun("a")
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix