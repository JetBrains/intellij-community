// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-sensitive-resolution
package test

enum class MyEnum { A, B }

fun test(e: MyEnum) {
    val result = e == <caret>MyEnum.A
}