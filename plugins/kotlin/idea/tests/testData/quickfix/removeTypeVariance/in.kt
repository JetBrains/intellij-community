// "Remove 'in' variance from 'T'" "true"
// WITH_STDLIB

class Test<in T> {
    fun foo(t: T) {}
    fun bar(): <caret>T = TODO()
}