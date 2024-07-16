// PROBLEM: none
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
