// OPTION: 1
fun test() {
    val i = 1
    foo(bar<caret>(1))
}

fun foo(i: Int) {}

fun bar(i: Int) = 1
