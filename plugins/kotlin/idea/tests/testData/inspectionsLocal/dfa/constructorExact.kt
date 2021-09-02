// PROBLEM: Condition is always false
// FIX: none
fun test() {
    val x = X()
    if (<caret>x is Y) {

    }
}
open class X() {}
open class Y():X() {}