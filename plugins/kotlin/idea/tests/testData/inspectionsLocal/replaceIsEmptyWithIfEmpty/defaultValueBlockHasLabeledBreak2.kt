// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
// WITH_STDLIB
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