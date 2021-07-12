// PROBLEM: 'when' branch is always reachable
// WITH_RUNTIME
// FIX: none
fun test(obj : Int) {
    when (obj) {
        in 0 until 10 -> {}
        <caret>!in 0..9 -> {}
        20 -> {}
        else -> {}
    }
}
