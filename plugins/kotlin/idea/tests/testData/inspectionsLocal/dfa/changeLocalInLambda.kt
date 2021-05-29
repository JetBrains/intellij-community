// PROBLEM: none
// WITH_RUNTIME
fun test(p : String) {
    var x = false
    p.let { x = p.isEmpty() }
    if (<caret>x) {}
}
