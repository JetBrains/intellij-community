// AFTER-WARNING: Parameter 'y' is never used
class A(val x: String) {

    class C {<caret>}

    fun foo() {
    }

    constructor(x: String, y: Int) : this(x) {
    }
}