// "Import extension function 'Other.foo'" "true"
package p

class Other

open class Base {
    fun Other.foo() {}
}

interface SomeInterface {
    fun Other.defaultFun() {}
}

object ObjBase : Base(), SomeInterface

fun usage(c: Other) {
    c.<caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */