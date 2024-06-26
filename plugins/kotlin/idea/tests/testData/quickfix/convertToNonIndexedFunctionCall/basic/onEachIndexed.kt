// "Convert to 'onEach'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.onEachIndexed { <caret>index, s ->
        println(s)
    }
}