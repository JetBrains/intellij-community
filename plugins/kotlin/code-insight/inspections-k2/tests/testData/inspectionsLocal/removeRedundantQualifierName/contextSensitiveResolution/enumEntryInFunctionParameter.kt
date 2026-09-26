// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

enum class MyEnum { A, B }

fun expectsMyEnum(e: MyEnum) {}

fun test() {
    expectsMyEnum(<caret>MyEnum.A)
}