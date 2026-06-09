// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
class Foo {
    operator fun set(vararg x: Int, y: String) {}
}

fun foo(foo: Foo) {
    foo[<caret>1, 2, 4, 5] = "String"
}