// PROBLEM: Condition is always false
// FIX: none
// WITH_RUNTIME
fun test() {
    val xKClass = X::class
    val x = xKClass.java
    val y = Y::class.java
    if (<caret>x === y) {}
}
class X
class Y