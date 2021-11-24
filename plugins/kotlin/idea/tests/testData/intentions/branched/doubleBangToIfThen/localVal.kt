// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
fun main(args: Array<String>) {
    val a: String? = "A"
    a<caret>!!
}
