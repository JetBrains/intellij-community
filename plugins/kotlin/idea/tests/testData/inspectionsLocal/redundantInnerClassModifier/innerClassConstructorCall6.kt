package test

class Test {
    class Foo {
        <caret>inner class Bar
    }
}

fun Test.foo(t: Test) {
    Test.Foo().Bar()
}