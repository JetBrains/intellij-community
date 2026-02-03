open class Foo
abstract class AbstractFoo : Foo(), Iterable<Int>

fun test(a: Iterable<*>) {
    if (a is Foo) {
        a<caret>
    }
}
