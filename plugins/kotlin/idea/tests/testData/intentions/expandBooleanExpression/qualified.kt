// AFTER-WARNING: Name shadowed: b
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Variable 'b' is never used
fun test(b: Boolean) {
    val b = Foo().bar()<caret>
}

class Foo {
    fun bar() = true
}