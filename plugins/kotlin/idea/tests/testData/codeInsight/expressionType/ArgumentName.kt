fun foo(a: Int) {}

fun main() {
    foo(<caret>a = 42)
}

// K1_TYPE: a -> <html>Int</html>

// K2_TYPE: a -> Type is unknown