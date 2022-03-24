// AFTER-WARNING: Variable 'result' is never used
// WITH_STDLIB
class X {
    fun one() {}
    fun two() = ""
}
fun test(x : X) {
    x.one()
    val result = process(<caret>x.two())
}
fun process(x: Any) = x