// WITH_RUNTIME
fun foo() {
    val x = 1..4

    x.forEach<caret><Int>({ it })
}