class Foo {
    operator fun get(a: Int, b: String): Int = 0
}

fun bar(b: String, a: Int) {
    Foo()[<caret>]
}

// ELEMENT: "a, b"
