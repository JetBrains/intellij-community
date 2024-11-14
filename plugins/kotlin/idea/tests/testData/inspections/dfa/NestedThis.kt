// WITH_STDLIB
fun Int.test() {
    val cond1 = this == 1
    if (cond1 && <warning descr="Condition 'this > 0' is always true when reached">this > 0</warning>) {}
    val cond2 = this.run { this == 1 }
    if (cond1 && <warning descr="Condition 'cond2' is always true when reached">cond2</warning>) {}
}