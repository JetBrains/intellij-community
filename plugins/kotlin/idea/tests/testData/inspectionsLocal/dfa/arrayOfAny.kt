// PROBLEM: none
// WITH_RUNTIME
fun test(x : Array<Any>) {
    val v = x[0]
    if (<caret>v is X) {
    }
}
data class X(val x: Int)