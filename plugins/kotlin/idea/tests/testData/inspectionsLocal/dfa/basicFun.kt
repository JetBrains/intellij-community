// PROBLEM: none
fun test() {
    var x = 5
    fun foo() {
        x++
    }
    foo()
    if (<caret>x == 5) {}
}