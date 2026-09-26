// WITH_STDLIB

annotation class Fancy

class Foo(@get:Fancy val foo: Int, @param:Fancy val foo1: Int, @set:Fancy val foo2: Int)

fun bar() {
    Foo(<caret>)
}
