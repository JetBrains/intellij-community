// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test() {
    val x = X::class.java
    val y = Y::class.java
    if (<caret>x === y) {}
}
class X
class Y