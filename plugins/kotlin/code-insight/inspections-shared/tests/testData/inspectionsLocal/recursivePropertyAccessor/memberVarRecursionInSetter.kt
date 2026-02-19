// PROBLEM: Recursive property accessor
// FIX: Replace with 'field'

class Test {
    var foo: Int = 0
        get() = field
        set(value: Int) {
            <caret>foo = value
        }
}
