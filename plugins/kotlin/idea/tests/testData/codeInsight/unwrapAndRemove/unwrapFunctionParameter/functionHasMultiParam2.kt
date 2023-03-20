// OPTION: 0
fun test() {
    val i = 1
    foo(123, 456<caret>)
}

fun foo(i: Int) {}

fun baz(i: Int, j: Int) = 1
