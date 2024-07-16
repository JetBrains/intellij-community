// PROBLEM: none
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