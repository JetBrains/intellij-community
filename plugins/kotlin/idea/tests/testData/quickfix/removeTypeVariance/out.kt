// "Remove 'out' variance from 'T'" "true"
// WITH_STDLIB
// K2_ERROR: TYPE_VARIANCE_CONFLICT_ERROR

class Test<out T> {
    fun foo(t: <caret>T) {}
    fun bar(): T = TODO()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix