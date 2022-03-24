// AFTER-WARNING: Parameter 's' is never used
fun println(s: String) {}

fun foo(y: Boolean) {
    <caret>if (!y) return
    println("no1")
    return
}