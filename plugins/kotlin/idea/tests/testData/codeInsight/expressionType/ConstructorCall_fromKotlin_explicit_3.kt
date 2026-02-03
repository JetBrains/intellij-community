class Foo {
    constructor()
}

fun usage() {
    Fo<caret>o()
}

// K1_TYPE: Foo() -> <html>Foo</html>

// K2_TYPE: Foo() -> <b>Foo</b>
