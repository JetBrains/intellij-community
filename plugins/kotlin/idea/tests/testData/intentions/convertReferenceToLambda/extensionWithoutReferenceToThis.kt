// WITH_STDLIB

class A {
    fun foo(x: String) {
        println(x)
    }

    fun main() {
        "doo".apply(this::<caret>foo)
    }
}