// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String, foo: Foo) {
    foo.bar<caret>(if (b) x else y)
}

class Foo {
    fun bar(s: String) {}
}