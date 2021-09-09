// WITH_RUNTIME
fun basicMath(x: Int, y: Int) {
    if (x == 12 && y == 2) {
        if (<warning descr="Condition is always true">x + y == 14</warning>) {}
        if (<warning descr="Condition is always true">x - y == 10</warning>) {}
        if (<warning descr="Condition is always true">x * y == 24</warning>) {}
        if (<warning descr="Condition is always true">x / y == 6</warning>) {}
        if (<warning descr="Condition is always true"><warning descr="Value is always zero">x % y</warning> == 0</warning>) {}
        if (<warning descr="Condition is always true">x shl y == 48</warning>) {}
        if (<warning descr="Condition is always true">x shr y == 3</warning>) {}
        if (<warning descr="Condition is always true">x ushr y == 3</warning>) {}
    }
}
fun basicMathLong(x: Long, y: Long) {
    if (x == 12L && y == 2L) {
        if (<warning descr="Condition is always true">x + y == 14L</warning>) {}
        if (<warning descr="Condition is always true">x - y == 10L</warning>) {}
        if (<warning descr="Condition is always true">x * y == 24L</warning>) {}
        if (<warning descr="Condition is always true">x / y == 6L</warning>) {}
        if (<warning descr="Condition is always true"><warning descr="Value is always zero">x % y</warning> == 0L</warning>) {}
    }
}
fun test(x : Int) {
    var a : Int
    a = 3
    if (x > 0) {
        a += 2
    } else {
        a *= 3
    }
    if (<warning descr="Condition is always true">a == 5 || <warning descr="Condition is always true when reached">a == 9</warning></warning>) {}
}
fun divByZero(x : Int) {
    val y = 100 / x
    if (<warning descr="Condition is always false">x == 0</warning>) {

    }
    if (x == 1) {}
    println(y)
}
fun percLongInt(x: Long, y: Int) {
    val z = 0
    x % y
    if (<warning descr="Condition is always true">z == 0</warning>) {}
}
fun backPropagation(x : Int) {
    if (x + 1 > 5) {
        if (<warning descr="Condition is always false">x < 3</warning>) {
        }
    }
}
fun decrement() {
    var x = 10
    if (<warning descr="Condition is always true">x-- == 10</warning>) { }
    if (<warning descr="Condition is always true">x == 9</warning>) {}
    if (<warning descr="Condition is always true">--x == 8</warning>) {}
    if (<warning descr="Condition is always true">x == 8</warning>) {}
}
fun increment() {
    var x = 10
    if (<warning descr="Condition is always true">x++ == 10</warning>) { }
    if (<warning descr="Condition is always true">x == 11</warning>) {}
    if (<warning descr="Condition is always true">++x == 12</warning>) {}
    if (<warning descr="Condition is always true">x == 12</warning>) {}
}
fun increment(x: Int) {
    if (x < 0) return
    var y = x
    ++y
    if (<warning descr="Condition is always false">y == 0</warning>) {}
}
fun oddity(x : Int) : Boolean {
    if (x % 2 == 0) {
        return true;
    }
    return <warning descr="Condition is always false">x == 10</warning>;
}
fun shiftLeft(x: Int) {
    val y = x shl 2
    if (<warning descr="Condition is always false"><warning descr="Value is always zero">y % 4</warning> == 2</warning>) {}
}
fun unaryMinus() {
    val x = 10
    val y = -x
    if (<warning descr="Condition is always true">y == -10</warning>) {}
}