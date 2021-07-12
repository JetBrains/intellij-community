// PROBLEM: 'when' branch is never reachable
// WITH_RUNTIME
// FIX: none
fun test(obj : Int) {
    when (obj) {
        in 0 until 10 -> {}
        10 -> {}
        <caret>9 -> {}
    }
}
