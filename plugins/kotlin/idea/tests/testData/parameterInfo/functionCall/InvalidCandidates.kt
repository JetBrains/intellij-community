class Foo<T>
fun <K>  Foo<K>.foo(i: Int, s: String) {}
fun <K>  Foo<K>.foo(s: String) {}
fun m(f: Foo<String>) {
    f.foo(<caret>)
}

