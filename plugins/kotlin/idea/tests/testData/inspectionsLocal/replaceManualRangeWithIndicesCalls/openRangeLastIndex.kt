// WITH_STDLIB
// LANGUAGE_VERSION: 1.9
// PROBLEM: none
fun test(list: List<String>) {
    val x = 42 in 0<caret>..<list.lastIndex
}
