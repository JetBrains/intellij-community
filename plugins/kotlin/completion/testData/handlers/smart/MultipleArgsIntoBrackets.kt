class Foo {
    operator fun get(a: Int, b: String, c: String): Int = 0
}

fun bar(b: String, a: Int, c: String) {
    Foo()[<caret>]
}

// ELEMENT: "a, b, c"
