// IS_APPLICABLE: false
// WITH_STDLIB
class Foo {
    fun repeat(times: Int, action: (Int) -> Unit) {}
}

fun main() {
    val x = Foo()
    x.<caret>repeat(5) { println(it) }
}