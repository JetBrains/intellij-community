// PROBLEM: none
// ERROR: Expression 'a' of type 'Any' cannot be invoked as a function. The function 'invoke()' is not found
// ERROR: Expression 'a' of type 'Any' cannot be invoked as a function. The function 'invoke()' is not found
// K2_ERROR:

/* Currently is not supported */
data class Foo(val a: Any)

class Bar {
    operator fun invoke(): Int = 1
}

fun test(foo: Foo) {
    i<caret>f (foo.a is Bar) {
        foo.a() + foo.a()
    } else null
}