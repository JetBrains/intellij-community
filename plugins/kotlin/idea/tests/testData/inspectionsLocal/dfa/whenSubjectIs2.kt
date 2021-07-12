// PROBLEM: 'when' branch is never reachable
// FIX: none
fun test(obj : Any?) {
    if (obj is X) {
        when(obj) {
            <caret>is Y -> {}
        }
    }
}
class X {}
class Y {}