// WITH_STDLIB
// AFTER-WARNING: Parameter 'f' is never used
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 'i' is never used

class Foo {
    fun foo(i: Int) {}
}

fun bar(i: Int, f: Foo) {}

fun test(f: Foo) {
    val f2 = Foo()
    f.foo(1)<caret>
    bar(2, f)
    bar(3, f2)
}