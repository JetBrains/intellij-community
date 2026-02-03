// PROBLEM: none
class Foo() {
    var foo: Int? = null;
    constructor(a: Int) : this() <caret>{
        foo = a
    }
}