// IS_APPLICABLE: false
// WITH_STDLIB
class Foo {
    fun forEach(): Int = 0
}

fun main() {
    val x = Foo()

    <caret>x.forEach()
}