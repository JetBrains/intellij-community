// WITH_STDLIB
fun test(list: List<Int>): Int?<caret> {
    val x = list.mapNotNull {
        return@mapNotNull null
    }
    return 1
}