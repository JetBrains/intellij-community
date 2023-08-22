// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
val a: String? = "A"
fun main(args: Array<String>) {
    a<caret>!!
}
