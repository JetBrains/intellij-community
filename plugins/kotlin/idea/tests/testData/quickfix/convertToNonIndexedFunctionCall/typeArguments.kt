// "Convert to 'forEach'" "true"
// WITH_STDLIB
fun test(list: List<Int>) {
    list.forEachIndexed<Any> { <caret>index, value ->
        println(value)
    }
}