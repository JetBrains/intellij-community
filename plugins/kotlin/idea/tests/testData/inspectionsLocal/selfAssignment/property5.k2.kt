// PROBLEM: none
// WITH_STDLIB
// IGNORE_K1

class Test {
    var foo = 1
}

fun f(a: Test, b: Test) {
    <caret>a.foo = b.foo
}