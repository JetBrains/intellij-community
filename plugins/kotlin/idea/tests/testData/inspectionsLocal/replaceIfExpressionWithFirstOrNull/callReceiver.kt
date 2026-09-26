// PROBLEM: none
// WITH_STDLIB

fun makeChildren(): List<String> = listOf("Emma", "Liam")

fun test(): String? {
    return <caret>if (makeChildren().isNotEmpty()) {
        makeChildren()[0]
    } else {
        null
    }
}
