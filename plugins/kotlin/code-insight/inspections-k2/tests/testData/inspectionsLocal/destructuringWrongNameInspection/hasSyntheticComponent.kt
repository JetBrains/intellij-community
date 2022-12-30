data class Foo(val a: String, val b: Int) {
    operator fun component3() = a + b.toString()
}

fun bar(f: Foo) {
    val (q, <caret>a, z) = f
}