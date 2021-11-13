// "Convert to 'forEach'" "true"
// WITH_STDLIB
fun test(list: List<String>) {
    list.forEachIndexed { <caret>index, s ->
        when (s) {
            "a" -> return@forEachIndexed
            "b" -> return@forEachIndexed
            else -> println(s)
        }
    }
}