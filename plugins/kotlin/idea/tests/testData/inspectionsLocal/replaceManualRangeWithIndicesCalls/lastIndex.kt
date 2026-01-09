// WITH_STDLIB
fun foo() {
    val intArray = intArrayOf(1, 2, 3, 4, 5)
    for (i in 0.<caret>.intArray.lastIndex) {
        println(intArray[i])
    }
}