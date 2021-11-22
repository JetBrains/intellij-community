// WITH_RUNTIME
fun compareObjects(x: Any?, y: Any?) {
    if (<warning descr="Condition is always true">x == x</warning>) {}
    if (x == y) {
        if (x == y) {}
        if (x == null) {
            if (<warning descr="Condition is always true">y == null</warning>) {}
        }
    }
    val b = x == null
    if (b) {
        if (<warning descr="Condition is always true">x == null</warning>) {}
    }
    if (x === y) {
        if (<warning descr="Condition is always true">x === y</warning>) {}
    }
}
fun compareNoEquals(x: X, y: X) {
    if (x == y) {
        if (<warning descr="Condition is always true">x == y</warning>) {}
    }
    if (x === y) {
        if (<warning descr="Condition is always true">x === y</warning>) {}
    }
}
class X {}