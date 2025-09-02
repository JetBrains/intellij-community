// PROBLEM: none
operator fun Any?.div(other: Any): Any? = this

fun test(s: Any?): Any? {
    return s <caret>/ return null
}