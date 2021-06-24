// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test() {
    val x = X::class
    val y = Y::class
    if (<caret>x === y) {}
}
class X
class Y