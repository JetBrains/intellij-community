// PROBLEM: 'when' branch is never reachable
// FIX: none
fun test(obj : Int) {
    if (obj > 0) {
        when (obj) {
            3 -> {}
            2 -> {}
            1 -> {}
            <caret>0 -> {}
        }
    }
}
