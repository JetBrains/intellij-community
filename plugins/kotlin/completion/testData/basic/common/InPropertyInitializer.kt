class Foo(val a: Int, b: Int) {
    val e: Int = <caret>
}

// IGNORE_K2
// EXIST: a
// EXIST: b