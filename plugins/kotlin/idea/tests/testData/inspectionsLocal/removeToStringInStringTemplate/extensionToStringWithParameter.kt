// PROBLEM: none

fun Any.toString(s: String) = ""

fun test(): String {
    val a = 5
    return "a: ${a.<caret>toString("")}"
}