class Foo private constructor() {
    companion object {
        operator fun invoke(): String = "foo"
    }
}

fun test() {
    Fo<caret>o()
}

// K1_TYPE: Foo -> <html>Foo.Companion</html>
// K1_TYPE: Foo() -> <html>String</html>

// K2_TYPE: Foo -> Foo.Companion
// K2_TYPE: Foo() -> String
