// PROBLEM: Condition is always false
// FIX: none
fun test(obj : Any?) {
    when(obj) {
        is X -> {}
        is Y -> {}
        else -> {
            if (<caret>obj is X) {}
        }
    }
}
class X {}
class Y {}