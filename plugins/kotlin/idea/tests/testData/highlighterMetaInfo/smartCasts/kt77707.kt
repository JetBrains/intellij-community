// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
interface Foo {
    fun foo()
    val bar: Int
}

interface Bar : Foo {
    fun newFoo()
    val newBar: Int
}

fun explicit(f: Foo) {
    if (f is Bar) {
        f.foo()
        f.bar
        f.newFoo()
        f.newBar
    }
}

fun Foo.implicit() {
    if (this is Bar) {
        foo()
        bar
        newFoo()
        newBar
    }
}