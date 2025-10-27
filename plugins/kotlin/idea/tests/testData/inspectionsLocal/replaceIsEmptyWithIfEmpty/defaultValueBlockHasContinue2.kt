// WITH_STDLIB
fun test(listOne: List<String>, listTwo: List<String>) {
    for (i in 1..10) {
        val z = <caret>if (listOne.isEmpty()) {
            while (true) {
                listOf(listTwo.firstOrNull() ?: continue)
            }
        } else {
            listOne
        }
    }
}