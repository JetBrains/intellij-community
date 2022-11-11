// WITH_STDLIB
fun constants() {
  val v1 = Double.POSITIVE_INFINITY
  val v2 = Double.NEGATIVE_INFINITY
  if (<warning descr="Condition 'v1 < v2' is always false">v1 < v2</warning>) {}
  val a = Double.NaN
  println(<warning descr="Condition 'a == a' is always false">a == a</warning>)
}
fun nan(v: Double) {
  if (v == v) {}
  var v1 = v
  if (v1 == v1) {}
  if (v1 == v) {}
}
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
