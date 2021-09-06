// WITH_RUNTIME
fun testParameter(x : Int) {
    if (x > 5) {
        if (<warning descr="Condition is always false">x < 3</warning>) {
        }
        if (<warning descr="Condition is always false">x < 6</warning>) {}
        if (x <= 6) {}
    }
    if (!(x > 5)) {
        if (<warning descr="Condition is always false">x == 6</warning>) {}
    }
    if (<warning descr="Condition is always false">x > 5 && <warning descr="Condition is always false">x < 3</warning></warning>) { }
    if (<warning descr="Condition is always true">x > 3 || <warning descr="Condition is always true">x < 5</warning></warning>) { }
}
fun test(x : Int) {
    var y = x
    var b = false
    if (<warning descr="Condition is always false">y != x</warning>) {}
    println(<weak_warning descr="Condition is always false">b</weak_warning>)
}
