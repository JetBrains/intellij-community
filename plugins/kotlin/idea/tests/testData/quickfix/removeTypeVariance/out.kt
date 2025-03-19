// "Remove 'out' variance from 'T'" "true"
// WITH_STDLIB

class Test<out T> {
    fun foo(t: <caret>T) {}
    fun bar(): T = TODO()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix