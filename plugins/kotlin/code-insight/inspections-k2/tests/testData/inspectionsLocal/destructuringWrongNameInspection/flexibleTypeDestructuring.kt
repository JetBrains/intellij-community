// WITH_STDLIB
data class Foo(val a: String, val b: Int)

fun bar(f: Foo) {
    val (r, <caret>a) = java.util.concurrent.atomic.AtomicReference(f).get()
}