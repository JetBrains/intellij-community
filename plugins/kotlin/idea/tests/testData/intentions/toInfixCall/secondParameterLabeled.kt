// IS_APPLICABLE: false
// ERROR: 'infix' modifier is inapplicable on this function: must have a single value parameter
// K2_ERROR: INAPPLICABLE_INFIX_MODIFIER

class Foo {
    infix fun foo(x: Int = 0, y: Int = 0) {
    }
}

fun bar(baz: Foo) {
    baz.<caret>foo(y = 1)
}
