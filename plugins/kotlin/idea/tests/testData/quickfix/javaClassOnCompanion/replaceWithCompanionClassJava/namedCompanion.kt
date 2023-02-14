// "Replace with 'Bar::class.java'" "true"
// WITH_STDLIB
class Foo {
    companion object Bar
}

fun test() {
    Foo.javaClass<caret>
}