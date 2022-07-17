// AFTER-WARNING: Parameter 'args' is never used
var a: String? = "A"
fun main(args: Array<String>) {
    a<caret>?.length
}
