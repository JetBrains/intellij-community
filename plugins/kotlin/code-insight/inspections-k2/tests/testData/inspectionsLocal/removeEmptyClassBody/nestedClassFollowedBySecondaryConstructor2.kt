// PROBLEM: none

class A(val x: String) {

    class C {<caret>}

    // comments

    constructor(x: String, y: Int) : this(x) {
    }
}