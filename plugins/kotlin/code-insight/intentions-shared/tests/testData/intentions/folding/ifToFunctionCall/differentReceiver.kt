// IS_APPLICABLE: false
fun test(b: Boolean, x: String, y: String, foo: Foo, foo2: Foo) {
    <caret>if (b) foo.bar(x) else foo2.bar(y)
}

class Foo {
    fun bar(s: String) {}
}