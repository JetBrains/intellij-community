class Foo // implicit primary constructor

fun usage() {
    Fo<caret>o()
}

// K1_TYPE: Foo -> <html>Type is unknown</html>
// K1_TYPE: Foo() -> <html>Foo</html>

// K2_TYPE: Foo -> Type is unknown
// K2_TYPE: Foo() -> Foo
