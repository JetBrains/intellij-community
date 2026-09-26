// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

sealed class MySealedClass {
    data object SubObject : MySealedClass()
}

fun expectsMySealedClass(s: MySealedClass) {}

fun test() {
    expectsMySealedClass(<caret>MySealedClass.SubObject)
}