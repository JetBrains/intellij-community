// "Remove 'is'" "true"
enum class Foo { A }

fun test(foo: Foo): Int = when (foo) {
    is <caret>Foo.A -> 1
    else -> 2
}
