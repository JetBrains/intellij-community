// WITH_STDLIB
fun foo(x: Int) {
    if (x !in 1..10<caret>) {
        println("not in range")
    }
}