// WITH_STDLIB
fun test(x : Int, y: Array<Int>) {
    if (x == 0) {
        other(x)
        y[x] = 1
        y[<weak_warning descr="Value of 'x' is always zero">x</weak_warning> + 1] = 2
        val <warning descr="[UNUSED_VARIABLE] Variable 'z' is never used">z</warning> = <weak_warning descr="Value of 'x' is always zero">x</weak_warning> + 1
    }
}
fun other(<warning descr="[UNUSED_PARAMETER] Parameter 'x' is never used">x</warning>: Int) {}