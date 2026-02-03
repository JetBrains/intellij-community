// PROBLEM: none

fun test(): String {
    val a: Any? = null
    return "a: ${a.<caret>toString()}"
}