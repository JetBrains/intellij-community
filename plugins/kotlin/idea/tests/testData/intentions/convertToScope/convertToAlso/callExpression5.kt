// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 'i' is never used

class Foo {
    fun foo(i: Int) {}
}

fun bar(i: Int, f: Foo) {}

fun test() {
    listOf(1).forEach {
        val f = Foo()<caret>
        f.foo(1)
        f.foo(2)
        bar(it, f)
    }
}