// WITH_STDLIB
// PROBLEM: none

fun test() {
    val result = mutableListOf<List<String>>()
    result +=<caret> listOf(1, 2, 3).map { it.toString() }
}