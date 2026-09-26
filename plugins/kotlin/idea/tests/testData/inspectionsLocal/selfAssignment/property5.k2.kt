// PROBLEM: none
// WITH_STDLIB


class Test {
    var foo = 1
}

fun f(a: Test, b: Test) {
    <caret>a.foo = b.foo
}