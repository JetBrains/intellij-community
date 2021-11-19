// WITH_STDLIB
fun arrayCast(x: Array<Int>) {
    @Suppress("UNCHECKED_CAST")
    val y = x as Array<Any>
    println(y)
}

fun arrayRead(x : Array<Int>) {
    if (x[0] > 10)
        if (<warning descr="Condition 'x[0] < 0' is always false">x[0] < 0</warning>) {

    }
}
fun arrayWrite(x: IntArray) {
    x[0] += 1
    if (x[0] == 1) {}
    x[0] = 1
    if (<warning descr="Condition 'x[0] == 1' is always true">x[0] == 1</warning>) {}
}
fun indexBounds(x : Array<Int>, y : Int) {
    if (x[y] > 10) {
        if (<warning descr="Condition 'y >= 0' is always true">y >= 0</warning>) {}
        if (<warning descr="Condition 'y == x.size' is always false">y == x.size</warning>) {}
        if (<warning descr="Condition 'y > x.size' is always false">y > x.size</warning>) {}
    }
}
fun indexBoundsNullable(x : Array<Int>, y : Int?) {
    if (y != null && x[y] > 10) {
        if (<warning descr="Condition 'y >= x.size' is always false">y >= x.size</warning>) {}
        if (<warning descr="Condition 'y < 0' is always false">y < 0</warning>) {}
    }
}
fun aioobe(x : Array<Int>, y : Int) {
    if (y >= x.size) {
        if (x[<warning descr="Index is always out of bounds">y</warning>] > 10) {
        }
    }
    if (y < 0) {
        if (x[<warning descr="Index is always out of bounds">y</warning>] > 10) {}
    }
}
fun aioobeNullable(x : Array<Int>, y : Int?) {
    if (y != null && y < 0) {
        if (x[<warning descr="Index is always out of bounds">y</warning>] > 10) {

        }
    }
}
fun arrayOfAny(x : Array<Any>) {
    val v = x[0]
    if (v is X) {
    }
}
fun nullableArraySize(x : Array<Int>?) {
    if (x?.size ?: 0 > 0) {

    }
}
data class X(val x: Int)