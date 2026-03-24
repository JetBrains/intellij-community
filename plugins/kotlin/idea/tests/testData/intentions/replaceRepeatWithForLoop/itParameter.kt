// WITH_STDLIB
fun foo() {
    val i = 0
    <caret>repeat(5) {
        println(i)
        println(it)
    }
}