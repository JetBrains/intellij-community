// "Rename to _" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

class Foo(val value: String)

fun test(foo: Foo) {
    val (<caret>value) = foo
}