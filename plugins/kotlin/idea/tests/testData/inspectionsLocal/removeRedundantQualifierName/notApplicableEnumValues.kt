// PROBLEM: none
// WITH_STDLIB
package packageName

import packageName.MyEnum.*

enum class MyEnum {
    A, B, C, D, E;
}

fun main() {
    for (value in <caret>MyEnum.values()) {
    }
}