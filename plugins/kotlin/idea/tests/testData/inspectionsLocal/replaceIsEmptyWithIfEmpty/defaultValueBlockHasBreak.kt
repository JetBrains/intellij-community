// PROBLEM: none
// WITH_STDLIB
fun test(listOne: List<String>, listTwo: List<String>) {
    for (i in 1..10) {
        val z = if (listOne.isNotEmpty<caret>()) {
            listOne
        } else {
            listOf(listTwo.firstOrNull() ?: break)
        }
    }
}