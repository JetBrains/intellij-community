// WITH_RUNTIME
fun compareObjects(x: Any?, y: Any?) {
    if (x == y) {
        if (x == y) {}
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