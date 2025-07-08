annotation class Fancy

class Foo(@Fancy foo: Int)

fun bar() {
    Foo(<caret>)
}
