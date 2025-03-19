// WITH_STDLIB
fun test(list: List<String>) {
    list.forEach { item ->
        println(item)<caret>
    }
}