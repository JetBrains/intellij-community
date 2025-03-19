// PROBLEM: none
// ERROR: Expression 'a' of type 'Any' cannot be invoked as a function. The function 'invoke()' is not found
// K2_ERROR:

/* Currently is not supported */
data class Foo(val a: Any)

class Bar {
    operator fun invoke() {}
}

fun test(foo: Foo) {
    i<caret>f (foo.a is Bar) {
        foo.a()
    } else null
}
