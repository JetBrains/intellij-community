// WITH_RUNTIME
fun test(x: Double, y: Double) {
    if (x > y) {}
    else if (x == y) {}
    else if (x < y) {}
    else {
        // x or y is NaN
    }
}
fun test1(x: Double) : Boolean = x > 5
fun test2(x: Double) : Boolean = x > 5 && <warning descr="Condition is always true when reached">x > 4</warning>