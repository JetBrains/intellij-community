// PROBLEM: none
// WITH_STDLIB

fun getList(): List<String> = listOf("Emma", "Liam")

fun test(): String? {
    return <caret>if (getList().size > 0) {
        getList()[0]
    } else {
        null
    }
}
