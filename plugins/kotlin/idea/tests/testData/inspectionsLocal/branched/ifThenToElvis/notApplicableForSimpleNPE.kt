// PROBLEM: none
// WITH_STDLIB
// DISABLE_ERRORS
fun main(args: Array<String>) {
    val t: String? = "abc"
    if (t == null<caret>) throw NullPointerException() else t
}
