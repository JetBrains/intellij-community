// PROBLEM: none
fun test(x : X) {
    if (<caret>"$x" == "hello") {}
}
class X {
    override fun toString(): String = "hello"
}