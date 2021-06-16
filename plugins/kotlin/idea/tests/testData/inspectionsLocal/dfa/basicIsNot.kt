// PROBLEM: Condition is always true
// FIX: none
fun test(obj : Any) {
    if (obj is X) {
        if (<caret>obj !is Y) {

        }
    }
}
class X {}
class Y {}