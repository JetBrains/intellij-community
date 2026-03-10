// "Remove 'out' variance from 'T'" "true"
// WITH_STDLIB
// K2_ERROR: Type parameter 'T' is declared as 'out' but occurs in 'in' position in type 'T (of class Test<out T>)'.

class Test<out T> {
    fun foo(t: <caret>T) {}
    fun bar(): T = TODO()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix