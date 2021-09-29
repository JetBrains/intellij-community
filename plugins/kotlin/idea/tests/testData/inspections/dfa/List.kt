// WITH_RUNTIME
fun listIsEmpty(x: List<Int>): Unit {
    if (x.size == 0) return
    if (<warning descr="Condition is always false">x.isEmpty()</warning>) {}
    if (<warning descr="Condition is always true">x.isNotEmpty()</warning>) {}
}
fun boxing(x: List<Int>?, y: Boolean) {
    if (x?.isEmpty() == y) {}
}
fun listWrite(x: MutableList<Int>, z: Int, y: Int) {
    if (z == 0) {
        x[0] = <weak_warning descr="Value is always zero">z</weak_warning> - 1
        x[y] = <weak_warning descr="Value is always zero">z</weak_warning> - 1
    }
}
fun listAccess(list: List<String>, idx: Int) {
    val s = list[idx]
    if (<warning descr="Condition is always false">idx > list.size</warning>) {}
    println(s)
    if (idx > list.size) {}
}
