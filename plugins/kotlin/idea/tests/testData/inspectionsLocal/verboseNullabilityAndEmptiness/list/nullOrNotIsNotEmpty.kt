// WITH_STDLIB
fun test(b: Boolean) {
    val list = list(b)
    if (<caret>list == null || !list.isNotEmpty()) println(0) else println(list.size)
}

fun list(b: Boolean): List<Int>? = if (b) emptyList() else null
