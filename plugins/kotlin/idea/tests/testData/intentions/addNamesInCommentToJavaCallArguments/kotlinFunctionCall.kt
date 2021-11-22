// IS_APPLICABLE: false
fun foo(bar: Int, baz: Int) {}

fun test() {
    foo<caret>(1, 2)
}