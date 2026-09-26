// AFTER-WARNING: Parameter 'args' is never used
// AFTER-WARNING: Parameter 'v' is never used
var a: String?
    get() = ""
    set(v) {}

fun main(args: Array<String>) {
    a?.<caret>length
}
