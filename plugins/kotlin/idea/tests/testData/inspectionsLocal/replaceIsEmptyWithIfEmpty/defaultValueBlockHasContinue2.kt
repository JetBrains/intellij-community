// WITH_STDLIB
fun test(listOne: List<String>, listTwo: List<String>) {
    for (i in 1..10) {
        val z = if (listOne.isEmpty<caret>()) {
            while (true) {
                listOf(listTwo.firstOrNull() ?: continue)
            }
        } else {
            listOne
        }
    }
}