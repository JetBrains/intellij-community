// WITH_STDLIB
// PROBLEM: none
package test

class MyClass(val javaClass: String)

fun usage(a: MyClass) {
    a::javaClass<caret>
}