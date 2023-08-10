package library
class Foo(n: Int) {
    companion object
}

operator fun Foo.Companion.invoke() = 1

