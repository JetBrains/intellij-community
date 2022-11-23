// IGNORE_FE10
// IS_APPLICABLE: false
class Foo(private val bar: Int) {
    operator fun plus(tail: String): String = "$bar.$tail"
}

fun test() {
    val foo = Foo(10)
    val fooWithTail = foo <caret>+ "tail"
}