// HIGHLIGHT: WARNING
// FIX: Replace 'if' expression with safe access expression
class F(a: Int?) {
    val b = a
    val c = i<caret>f (b != null) b.toString() else null
}

fun main(args: Array<String>) {
    F(1).c
}
