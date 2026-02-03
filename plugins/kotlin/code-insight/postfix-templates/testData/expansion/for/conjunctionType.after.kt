open class Foo
abstract class AbstractFoo : Foo(), Iterable<Int>

fun test(a: Iterable<*>) {
    if (a is Foo) {
        for (<selection>any<caret></selection> in a) {

        }
    }
}
