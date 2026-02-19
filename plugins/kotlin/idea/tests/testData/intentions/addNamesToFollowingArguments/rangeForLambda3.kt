// IS_APPLICABLE: false

fun foo(a: Int, handler: () -> Unit, b: Int){}

fun bar() {
    foo(1, {<caret>}, 2)
}