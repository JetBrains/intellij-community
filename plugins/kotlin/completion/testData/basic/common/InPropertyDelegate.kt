class Foo(val a: Int, b: Int) {
    val e: Int
        by <caret>
}

// EXIST: a
// EXIST: b