// WITH_RUNTIME
fun test(x : Int?) {
    if (x != null && x > 5) {}
    if (<warning descr="Condition is always false">x != null && x > 5 && <warning descr="Condition is always false when reached">x < 3</warning></warning>) {}
}
fun test(y : Int, z: Int) {
    var x : Int? = null
    if (z == 2) x = y
    if (<warning descr="Condition is always false">z == 2 && <warning descr="Condition is always false when reached">x == null</warning></warning>) {}
}
fun test(x:Int?, y:Int) {
    if (x != null && y >= x) { }
}
fun elvis(x: Int?) {
    val y = x ?: 2
    if (x == null) {
        if (<warning descr="Condition is always true">y == 2</warning>) {

        }
    }
}