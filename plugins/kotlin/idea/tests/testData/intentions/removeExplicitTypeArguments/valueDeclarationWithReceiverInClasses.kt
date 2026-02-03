// PROBLEM: none
// IS_APPLICABLE: false
class Foo<T>(val name: String) {
    fun <TT : T> bar() {
        val x = "From $name"
    }
}

fun Foo<String>.testOne() {
    fun Foo<Int>.testTwo() {
        bar<caret><Int>() // receiver is this@testTwo
        bar<String>() // receiver is this@testOne
    }

    Foo<Int>("FooInt").testTwo()
}

fun main() {
    Foo<String>("FooString").testOne()
}