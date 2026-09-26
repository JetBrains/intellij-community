// IS_APPLICABLE: false
// WITH_STDLIB
class Foo {
    operator fun iterator(): Iterator<Int> = TODO()
}

fun test() {
    <caret>for (x in Foo()) {
        println(x)
    }
}
