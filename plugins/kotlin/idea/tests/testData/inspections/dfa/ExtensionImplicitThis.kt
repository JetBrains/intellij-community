// WITH_STDLIB
data class Point(val x: Int, val y: Int)

fun Point.test() {
    val b = <warning descr="Condition 'x > this.x' is always false">x > this.x</warning>
}

fun String.test() {
    val b = <warning descr="Condition 'length > this.length' is always false">length > this.length</warning>
}