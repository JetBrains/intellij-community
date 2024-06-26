// "Convert to 'forEach'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.forEachIndexed { <caret>index, s ->
        println(s)
    }
}