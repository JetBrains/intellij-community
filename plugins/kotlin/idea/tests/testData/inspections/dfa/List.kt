// WITH_RUNTIME
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
