data class Foo(val a: String, val b: Int, val c: String)

fun bar(f: Foo) {
    val (a, <caret>c) = f
}