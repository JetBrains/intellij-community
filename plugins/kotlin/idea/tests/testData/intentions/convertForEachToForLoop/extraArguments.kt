// IS_APPLICABLE: false
// WITH_STDLIB
class Foo {
    fun forEach(predicate: (Int) -> Int, bar: Int): Int = predicate(bar)
}

fun main() {
    val x = Foo()

    <caret>x.forEach({ it * 2 }, 2)
}