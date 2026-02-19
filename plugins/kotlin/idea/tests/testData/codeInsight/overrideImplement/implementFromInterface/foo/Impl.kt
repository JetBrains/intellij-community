package foo

abstract class A {
    abstract fun <caret>foo()
}


// MEMBER_K2: "foo(): Unit"
// IGNORE_K1