// "Replace with '!='" "true"
enum class Foo { A }

fun test(foo: Foo): Boolean = foo !is <caret>Foo.A
