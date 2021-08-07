// PROBLEM: Condition is always true
// FIX: none
fun test(obj : Any?) {
    if (obj is X?) {
        if (obj is Y?) {
            if (<caret>obj == null) {

            }
        }
    }
}
class X {}
class Y {}