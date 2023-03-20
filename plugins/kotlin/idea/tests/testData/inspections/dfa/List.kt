// WITH_STDLIB
fun complexQualifier() {
    val map = mutableMapOf(1 to mutableListOf<Int>())
    val list = mutableListOf<Int>()
    (map[2] ?: list).add(3)
    if (list.isNotEmpty()) println()
}
fun listIsEmpty(x: List<Int>): Unit {
    if (x.size == 0) return
    if (<warning descr="Condition 'x.isEmpty()' is always false">x.isEmpty()</warning>) {}
    if (<warning descr="Condition 'x.isNotEmpty()' is always true">x.isNotEmpty()</warning>) {}
}
fun boxing(x: List<Int>?, y: Boolean) {
    if (x?.isEmpty() == y) {}
}
fun listWrite(x: MutableList<Int>, z: Int, y: Int) {
    if (z == 0) {
        x[0] = <weak_warning descr="Value of 'z' is always zero">z</weak_warning> - 1
        x[y] = <weak_warning descr="Value of 'z' is always zero">z</weak_warning> - 1
    }
}
fun listAccess(list: List<String>, idx: Int) {
    val s = list[idx]
    if (<warning descr="Condition 'idx > list.size' is always false">idx > list.size</warning>) {}
    println(s)
    if (idx > list.size) {}
}
