// WITH_STDLIB
fun castMayFail(b: Boolean) {
    val x : Any = if (b) "x" else 5
    val y = x as String
    if (<warning descr="Condition 'b' is always true">b</warning>) {}
    println(y)
}
fun castWillFail(b: Boolean) {
    val x : Any = if (b) X() else Y()
    val y : Any = if (b) x <warning descr="Cast will always fail">as</warning> Y else X()
    println(y)
}
fun castNumber() {
    val c: Number = 1
    c <warning descr="Cast will always fail">as</warning> Float
}
fun castIntToFloat(c: Int) {
    // Difference from K1: another diagnostic
    c <warning descr="[NUMERIC_CAST_NEVER_SUCCEEDS_BUT_CAN_BE_REPLACED_WITH_TO_CALL] This cast can never succeed. Use 'toFloat' to perform numeric conversion.">as Float</warning>
}
fun safeCast(b: Boolean) {
    val x : Any = if (b) "x" else 5
    val y = x as? String
    if (y == null) {
        if (<warning descr="Condition 'b' is always false">b</warning>) {}
    }
}
fun nullAsNullableVoid() {
    // No warning: cast to set type
    val x = null as Void?
    println(x)
}
fun safeAs(v : Any?) {
    val b = (v as? String)
    if (v == null) { }
    println(b)
}
class X {}
class Y {}