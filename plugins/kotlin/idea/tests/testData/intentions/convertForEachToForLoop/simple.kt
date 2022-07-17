// WITH_STDLIB
fun foo() {
    val x = 1..4

    x.forEach<caret> { it.equals(1) }
}