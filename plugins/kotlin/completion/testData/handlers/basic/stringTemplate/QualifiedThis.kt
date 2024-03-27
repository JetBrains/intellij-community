// FIR_IDENTICAL
// FIR_COMPARISON
class Outer{
    fun String.doSomething()
    {
        "$this<caret>"
    }
}
// ELEMENT: this@Outer
