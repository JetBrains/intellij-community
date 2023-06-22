// WITH_STDLIB
fun build2() {
    val list = mutableListOf<Int>().apply {}
    if (<warning descr="Condition 'list.size > 0' is always false"><weak_warning descr="Value of 'list.size' is always zero">list.size</weak_warning> > 0</warning>) { }
}
fun build3() {
    val list = mutableListOf<Int>().also {}
    if (<warning descr="Condition 'list.size > 0' is always false"><weak_warning descr="Value of 'list.size' is always zero">list.size</weak_warning> > 0</warning>) {
        println("ho")
    }
}
fun build() {
    val list = mutableListOf<Int>().apply {
        add(123)
    }
    if (list.size > 0) { }
}
