// PROBLEM: Variable 'foo' is assigned to itself
// WITH_STDLIB
// FIX: Remove self assignment
// IGNORE_K1

class Test {
    var foo = 1
}

fun f(a: Test) {
    <caret>a.foo = a.foo
}