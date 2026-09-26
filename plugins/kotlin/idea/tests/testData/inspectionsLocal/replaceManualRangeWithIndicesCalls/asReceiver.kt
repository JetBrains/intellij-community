// WITH_STDLIB
fun foo(list: List<String>) {
    (0 <caret>until list.size).forEach {
        println(it)
    }
}