data class Foo(val a: String, val b: Int)

fun bar(f: Foo) {
    val (<caret>b, c) = f
}