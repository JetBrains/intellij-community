// AFTER-WARNING: Parameter 'a' is never used
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 'd' is never used
class Baz

fun foo(a: Int, b: String, d: Baz) {

}

class TestClass {
    val <caret>prop1 = ::foo
}