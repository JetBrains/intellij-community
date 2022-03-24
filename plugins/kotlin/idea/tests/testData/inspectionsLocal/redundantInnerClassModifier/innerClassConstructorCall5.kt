package test

class Test {
    <caret>inner class Foo {
        inner class Bar
    }
}

fun Test.foo(t: Test) {
    Foo().Bar()
}