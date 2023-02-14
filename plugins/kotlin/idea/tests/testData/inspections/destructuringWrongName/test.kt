data class Foo(val a: String, val b: Int, val c: String)

fun bar(f: Foo) {
    val (a, c) = f
}

// not applicable to non-data classes
class Foo2(val a: String, val b: Int, val c: String) {
    operator fun component1() = a
    operator fun component2() = b
}

fun bar2(f: Foo2) {
    val (a, c) = f
}