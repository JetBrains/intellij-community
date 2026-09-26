// FIR_IDENTICAL

import dependency.a

fun foo(a: Int) {}

fun test() {
    foo(<caret>)
}

// ABSENT: { itemText: "a = a" }
// EXIST: { itemText: "a =", tailText: " Int" }
