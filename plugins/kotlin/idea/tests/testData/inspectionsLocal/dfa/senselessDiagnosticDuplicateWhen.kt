// PROBLEM: none
fun test(x : X) {
    // Reported as a compiler warning: suppress
    when (x) {
        <caret>is X -> {}
    }
}
class X