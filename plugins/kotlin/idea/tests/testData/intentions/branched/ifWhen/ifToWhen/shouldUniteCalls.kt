// IGNORE_K1
// WITH_STDLIB

fun foo(x: Int) {
    <caret>if (x == 42) {
        println("42")
    }
    if (x == 239) {
        println("239")
    }
}