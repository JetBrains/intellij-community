// IS_APPLICABLE: false
package com.example

class MyClass(val value: Int)

fun f(body: (Int) -> com.example.MyClass) {}

fun test() {
    f { i -> <caret>com.example.MyClass(i) }
}
