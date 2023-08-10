// PROBLEM: Recursive property accessor
// FIX: none
class A

class Test {
    var A.foo: Int
        get() = 2
        set(value: Int) {
            <caret>foo = value
        }
}