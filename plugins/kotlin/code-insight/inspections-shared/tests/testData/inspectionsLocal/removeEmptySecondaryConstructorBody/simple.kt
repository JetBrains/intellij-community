// AFTER-WARNING: Parameter 'a' is never used
class Foo() {
    constructor(a: Int) : this() <caret>{
    }
}