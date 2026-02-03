// WITH_STDLIB
fun test(list: List<String>, arr: IntArray) {
    for (idx in list.indices) {
        if (<warning descr="Condition 'idx < 0' is always false">idx < 0</warning>) {}

    }
    for (idx in arr.indices) {
        if (<warning descr="Condition 'idx == arr.size' is always false">idx == arr.size</warning>) {}
    }
}