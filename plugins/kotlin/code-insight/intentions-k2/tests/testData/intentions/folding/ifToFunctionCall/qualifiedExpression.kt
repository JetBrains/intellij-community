// AFTER-WARNING: Parameter 's' is never used
fun test(b: Boolean, x: String, y: String, foo: Foo) {
    <caret>if (b) foo.bar(x) else foo.bar(y)
}

class Foo {
    fun bar(s: String) {}
}