// PROBLEM: none
// WITH_STDLIB
package packageName

import packageName.MyEnum.*

enum class MyEnum {
    A, B, C, D, E;
}

fun main() {
    <caret>MyEnum.valueOf("A")
}