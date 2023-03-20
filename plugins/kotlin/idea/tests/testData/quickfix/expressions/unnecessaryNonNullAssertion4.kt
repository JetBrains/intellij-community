// "Remove unnecessary non-null assertion (!!)" "true"
class Foo {
    val foo: String = ""
}

fun bar(i: Int) {}

fun test(foo: Foo?) {
    bar(
        foo
        !!.foo!!<caret>.length
    )
}
