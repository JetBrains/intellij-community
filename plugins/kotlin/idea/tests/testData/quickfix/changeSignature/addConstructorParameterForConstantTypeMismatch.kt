// "Add 1st parameter to constructor 'Foo'" "true"

class Foo(val name: String)

fun test() {
    val foo = Foo(<caret>1, "name")
}