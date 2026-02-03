// WITH_STDLIB
fun test(x : Int, y: Array<Int>) {
    if (x == 0) {
        other(x)
        y[x] = 1
        y[<weak_warning descr="Value of 'x' is always zero">x</weak_warning> + 1] = 2
        val z = <weak_warning descr="Value of 'x' is always zero">x</weak_warning> + 1
        println(z)
    }
}
fun other(x: Int) {
    println(x)
}