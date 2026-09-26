// PROBLEM: none
// WITH_STDLIB

interface Foo {
    val message: String
    fun test() { println(message) }
}

class FooImpl : Foo {
    override val message = "FooImpl"
}

class Bar(foo: Foo) : Foo by foo {
    override val message = "Bar"
    <caret>override fun test() {
        super.test()
    }
}
