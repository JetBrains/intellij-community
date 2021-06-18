// PROBLEM: none
// WITH_RUNTIME
fun test(a:Long, b:Long): Boolean {
    return <caret>a in 1 until b
}
