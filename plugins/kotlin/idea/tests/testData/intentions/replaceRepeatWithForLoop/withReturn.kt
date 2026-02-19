// WITH_STDLIB
fun foo() {
    <caret>repeat(5) {
        if (it == 3) return@repeat
        println(it)
    }
}