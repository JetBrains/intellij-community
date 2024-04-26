// "Import extension function 'Companion.foo'" "true"
package p

class A {
    companion object
}

class B {
    companion object
}

open class P {
    fun A.Companion.foo() {}
}

open class Q {
    fun B.Companion.foo() {}
}

object PObject : P()
object QObject : Q()

fun usage() {
    A.<caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
/* IGNORE_K2 */