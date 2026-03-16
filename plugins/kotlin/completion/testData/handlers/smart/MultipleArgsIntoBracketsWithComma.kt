class Foo {
    operator fun get(a: Int, b: String, c: String, d: Double): Int = 0
}

fun bar(b: String, a: Int, c: String) {
    val foo = Foo()
    val f = foo[<caret>]
}

// ELEMENT: "a, b, c"
// IGNORE_K1