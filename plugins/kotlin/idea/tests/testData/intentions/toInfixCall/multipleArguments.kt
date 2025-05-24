// IS_APPLICABLE: false
// ERROR: 'infix' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: 'infix' modifier is inapplicable to this function.
interface Foo {
    infix fun foo(a: Int, b: Int)
}

fun foo(x: Foo) {
    x.<caret>foo(1, 2)
}
