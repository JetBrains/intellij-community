sealed class Foo
object Bar : Foo()
object Baz : Foo()
object Another : Foo()

fun test(foo: Foo) {
    when (foo) {
        <caret>Another -> TODO()
        else -> TODO()
    }
}