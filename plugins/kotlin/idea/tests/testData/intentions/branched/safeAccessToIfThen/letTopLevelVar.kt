// WITH_STDLIB
// AFTER-WARNING: Parameter 'args' is never used

var a: String? = "A"
fun main(args: Array<String>) {
    a<caret>?.let { it.length + 1 }
}