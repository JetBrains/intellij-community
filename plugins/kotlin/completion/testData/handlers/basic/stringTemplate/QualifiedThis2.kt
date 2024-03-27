// FIR_IDENTICAL
// FIR_COMPARISON
class Outer{
    inner class Inner {


        fun String.doSomething() {
            "$this<caret>"
        }
    }
}
// ELEMENT: this@Inner
