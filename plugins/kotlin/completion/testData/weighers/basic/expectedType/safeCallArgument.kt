package test

class Foo {
    fun foo(name: String) {}
}

fun test(foo: Foo?, arg1: Int, arg2: String) {
    foo?.foo(arg<caret>)
}

// ORDER: arg2
// ORDER: arg1
