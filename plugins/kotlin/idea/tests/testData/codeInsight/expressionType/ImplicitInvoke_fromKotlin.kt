class Foo {
    operator fun invoke(): String = "foo"
}

fun test(foo: Foo) {
    fo<caret>o()
}

// K1_TYPE: foo -> <html>Foo</html>
// K1_TYPE: foo() -> <html>String</html>

// K2_TYPE: foo -> <b>Foo</b>
// K2_TYPE: foo() -> <b>String</b>
