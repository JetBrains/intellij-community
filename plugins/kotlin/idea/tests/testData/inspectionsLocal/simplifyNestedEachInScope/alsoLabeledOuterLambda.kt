// WITH_STDLIB
fun test() {
    listOf(1, 2, 3).also<caret> myLabel@{ it.forEach { item ->
        if (item == 2) return@forEach
        println(item)
    } }
}

