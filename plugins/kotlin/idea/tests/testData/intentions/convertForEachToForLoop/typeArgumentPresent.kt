// WITH_STDLIB
// AFTER-WARNING: The expression is unused
fun foo() {
    val x = 1..4

    x.forEach<caret><Int>({ it })
}