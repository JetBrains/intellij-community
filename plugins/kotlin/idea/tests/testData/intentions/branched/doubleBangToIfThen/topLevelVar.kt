// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Variable 't' is never used
var a: String? = "A"
fun main(args: Array<String>) {
    val t = a<caret>!!
}
