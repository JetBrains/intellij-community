// WITH_STDLIB
fun test(x: Double, y: Double) {
    if (x > y) {}
    else if (x == y) {}
    else if (x < y) {}
    else {
        // x or y is NaN
    }
}
fun test1(x: Double) : Boolean = x > 5
fun test2(x: Double) : Boolean = x > 5 && <warning descr="Condition 'x > 4' is always true when reached">x > 4</warning>

fun Double?.isPositive(): Boolean {
    return this != null && this > 0
}

fun Double?.isPositive2(): Boolean {
    return this != null && this > 0.0
}

fun Double?.check(): Boolean {
    return <warning descr="Condition 'this != null && this < 0 && this > 10' is always false">this != null && this < 0 && <warning descr="Condition 'this > 10' is always false when reached">this > 10</warning></warning>
}
