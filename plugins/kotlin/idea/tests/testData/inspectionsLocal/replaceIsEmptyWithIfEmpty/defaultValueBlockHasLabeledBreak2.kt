// PROBLEM: none
// WITH_RUNTIME
fun test(listOne: List<String>, listTwo: List<String>) {
    outer@ for (i in 1..10) {
        val z = if (listOne.isNotEmpty<caret>()) {
            listOne
        } else {
            while (true) {
                listOf(listTwo.firstOrNull() ?: break@outer)
            }
        }
    }
}