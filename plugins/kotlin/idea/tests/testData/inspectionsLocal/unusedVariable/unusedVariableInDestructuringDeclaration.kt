// PROBLEM: none
fun test(condition: Boolean, foo: Foo) {
    val v = if (condition) {
        val (<caret>one, two) = foo
        two
    } else {
        null
    }
}

data class Foo(val one: String, val two: String)