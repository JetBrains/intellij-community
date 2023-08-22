// WITH_STDLIB
fun foo() {
    val x = "abcd"

    x.forEach<caret> { it.equals('a') }
}