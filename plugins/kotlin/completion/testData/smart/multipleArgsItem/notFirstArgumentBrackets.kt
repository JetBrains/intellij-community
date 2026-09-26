class Foo {
    operator fun get(a: Int, b: String, c: String): Int = 0
}


fun bar(b: String, a: Int, c: String) {
    val foo = Foo()
    val test = foo[a, <caret>]
}

// EXIST: "b, c"
